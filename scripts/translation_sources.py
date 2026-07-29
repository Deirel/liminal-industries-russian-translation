#!/usr/bin/env python3
"""Shared source-adapter contracts for version translation manifests."""

from __future__ import annotations

import io
import json
import re
import zipfile
from copy import deepcopy
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Callable

from build_initial_catalog import sha256_bytes, source_hash


SOURCE_CONFIG_SCHEMA = 1
PATCHOULI_TEXT_FIELDS = {
    "description",
    "landing_text",
    "link_text",
    "name",
    "subtitle",
    "text",
    "title",
}
PATCHOULI_ASSET_RE = re.compile(
    r"assets/([^/]+)/patchouli_books/([^/]+)/en_us/(.+\.json)$"
)
PATCHOULI_BOOK_RE = re.compile(
    r"data/([^/]+)/patchouli_books/([^/]+)/book\.json$"
)
IE_MANUAL_RE = re.compile(
    r"assets/([^/]+)/manual/en_us/(.+\.txt)$"
)
MANTLE_BOOK_RE = re.compile(
    r"assets/([^/]+)/book/([^/]+)/en_us/(.+\.json)$"
)
MANTLE_TEXT_FIELDS = {
    "effects",
    "properties",
    "subText",
    "text",
    "title",
    "tooltip",
}


@dataclass
class SourceResult:
    records: list[dict[str, Any]] = field(default_factory=list)
    source_files: list[dict[str, str]] = field(default_factory=list)
    report: dict[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class SourceDefinition:
    source_id: str
    adapter: str
    options: dict[str, Any]


SourceCollector = Callable[[SourceDefinition], SourceResult]


def load_source_definitions(path: Path) -> list[SourceDefinition]:
    document = json.loads(path.read_text(encoding="utf-8"))
    values = document.get("sources")
    if document.get("schema") != SOURCE_CONFIG_SCHEMA or not isinstance(values, list):
        raise ValueError(f"{path}: invalid translation source configuration")
    result: list[SourceDefinition] = []
    seen: set[str] = set()
    for index, value in enumerate(values):
        if not isinstance(value, dict):
            raise ValueError(f"{path}: sources[{index}] must be an object")
        source_id = value.get("id")
        adapter = value.get("adapter")
        if not isinstance(source_id, str) or not source_id:
            raise ValueError(f"{path}: sources[{index}] has no ID")
        if not isinstance(adapter, str) or not adapter:
            raise ValueError(f"{path}: source {source_id} has no adapter")
        if source_id in seen:
            raise ValueError(f"{path}: duplicate source ID {source_id}")
        seen.add(source_id)
        result.append(
            SourceDefinition(
                source_id,
                adapter,
                {
                    key: child
                    for key, child in value.items()
                    if key not in {"id", "adapter"}
                },
            )
        )
    return result


def collect_sources(
    definitions: list[SourceDefinition],
    collectors: dict[str, SourceCollector],
) -> SourceResult:
    combined = SourceResult(report={"sources": {}})
    seen_records: set[str] = set()
    seen_files: dict[str, str] = {}
    for definition in definitions:
        collector = collectors.get(definition.adapter)
        if collector is None:
            raise ValueError(
                f"{definition.source_id}: unknown source adapter {definition.adapter}"
            )
        result = collector(definition)
        for record in result.records:
            logical_id = record["id"]
            if logical_id in seen_records:
                raise ValueError(f"duplicate translation record {logical_id}")
            seen_records.add(logical_id)
            record["source_id"] = definition.source_id
            record["source_type"] = definition.adapter
            combined.records.append(record)
        for source_file in result.source_files:
            path = source_file["path"]
            digest = source_file["sha256"]
            previous = seen_files.get(path)
            if previous is not None and previous != digest:
                raise ValueError(f"conflicting source file hash for {path}")
            seen_files[path] = digest
        combined.report["sources"][definition.source_id] = {
            "adapter": definition.adapter,
            "records": len(result.records),
            **result.report,
        }
    combined.source_files = [
        {"path": path, "sha256": digest}
        for path, digest in sorted(seen_files.items())
    ]
    combined.records.sort(key=lambda record: record["id"])
    return combined


def iter_json_strings(
    value: Any,
    pointer: str = "",
    text_fields: set[str] = PATCHOULI_TEXT_FIELDS,
):
    if isinstance(value, dict):
        for key, child in value.items():
            escaped = key.replace("~", "~0").replace("/", "~1")
            child_pointer = f"{pointer}/{escaped}"
            if key in text_fields and isinstance(child, str) and child:
                yield child_pointer, child
            elif key in text_fields and isinstance(child, list):
                for index, entry in enumerate(child):
                    if isinstance(entry, str) and entry:
                        yield f"{child_pointer}/{index}", entry
            yield from iter_json_strings(child, child_pointer, text_fields)
    elif isinstance(value, list):
        for index, child in enumerate(value):
            yield from iter_json_strings(child, f"{pointer}/{index}", text_fields)


def json_pointer_get(value: Any, pointer: str) -> Any:
    current = value
    for token in pointer.removeprefix("/").split("/"):
        token = token.replace("~1", "/").replace("~0", "~")
        current = current[int(token)] if isinstance(current, list) else current[token]
    return current


def json_pointer_set(value: Any, pointer: str, replacement: str) -> None:
    tokens = [
        token.replace("~1", "/").replace("~0", "~")
        for token in pointer.removeprefix("/").split("/")
    ]
    current = value
    for token in tokens[:-1]:
        current = current[int(token)] if isinstance(current, list) else current[token]
    final = tokens[-1]
    if isinstance(current, list):
        current[int(final)] = replacement
    else:
        current[final] = replacement


def iter_mantle_strings(value: Any, pointer: str = ""):
    for child_pointer, text in iter_json_strings(
        value, pointer, MANTLE_TEXT_FIELDS
    ):
        if text.strip():
            yield child_pointer, text
    if isinstance(value, dict):
        if (
            value.get("action") == "add_group"
            and isinstance(value.get("data"), str)
            and value["data"].strip()
        ):
            yield f"{pointer}/data", value["data"]
        for key, child in value.items():
            escaped = key.replace("~", "~0").replace("/", "~1")
            yield from iter_mantle_group_labels(
                child, f"{pointer}/{escaped}"
            )


def iter_mantle_group_labels(value: Any, pointer: str):
    if isinstance(value, dict):
        if (
            value.get("action") == "add_group"
            and isinstance(value.get("data"), str)
            and value["data"].strip()
        ):
            yield f"{pointer}/data", value["data"]
        for key, child in value.items():
            escaped = key.replace("~", "~0").replace("/", "~1")
            yield from iter_mantle_group_labels(
                child, f"{pointer}/{escaped}"
            )
    elif isinstance(value, list):
        for index, child in enumerate(value):
            yield from iter_mantle_group_labels(
                child, f"{pointer}/{index}"
            )


def collect_patchouli(
    definition: SourceDefinition,
    archives: list[Path],
    instance_root: Path,
    en_us: dict[str, str],
    native_ru: dict[str, str],
    project_ru: dict[str, str] | None = None,
) -> SourceResult:
    del definition
    project_ru = project_ru or {}
    records_by_id: dict[str, dict[str, Any]] = {}
    source_files: dict[str, str] = {}
    native_documents: dict[tuple[Path, str | None, str], Any] = {}
    documents: list[tuple[Path, str | None, str, bytes]] = []
    book_documents: list[tuple[Path, str | None, str, bytes]] = []

    for archive in archives:
        try:
            with zipfile.ZipFile(archive) as jar:
                containers: list[
                    tuple[str | None, zipfile.ZipFile]
                ] = [(None, jar)]
                nested_handles: list[zipfile.ZipFile] = []
                for nested_member in jar.namelist():
                    if (
                        nested_member.startswith("META-INF/jarjar/")
                        and nested_member.endswith(".jar")
                    ):
                        nested = zipfile.ZipFile(
                            io.BytesIO(jar.read(nested_member))
                        )
                        nested_handles.append(nested)
                        containers.append((nested_member, nested))
                for nested_member, container in containers:
                    names = set(container.namelist())
                    for member in sorted(names):
                        asset_match = PATCHOULI_ASSET_RE.fullmatch(member)
                        book_match = PATCHOULI_BOOK_RE.fullmatch(member)
                        if asset_match:
                            data = container.read(member)
                            documents.append(
                                (archive, nested_member, member, data)
                            )
                            ru_member = member.replace("/en_us/", "/ru_ru/", 1)
                            if ru_member in names:
                                native_documents[
                                    (archive, nested_member, ru_member)
                                ] = json.loads(
                                    container.read(ru_member).decode("utf-8-sig")
                                )
                        elif book_match:
                            book_documents.append(
                                (
                                    archive,
                                    nested_member,
                                    member,
                                    container.read(member),
                                )
                            )
                for nested in nested_handles:
                    nested.close()
        except zipfile.BadZipFile:
            continue

    def add_language_record(
        namespace: str,
        key: str,
        source: str,
        location: dict[str, Any],
        kind: str,
    ) -> None:
        key_id = (
            key
            if re.fullmatch(r"[a-zA-Z0-9_.-]+", key)
            else source_hash(key).removeprefix("sha256:")[:20]
        )
        logical_id = f"patchouli-lang:{namespace}:{key_id}"
        record = {
            "id": logical_id,
            "kind": kind,
            "source": source,
            "source_hash": source_hash(source),
            "namespace": namespace,
            "translation_key": key,
            "native_ru_present": key in native_ru,
            "output_format": "lang",
            "location": location,
        }
        if key in project_ru:
            record["suggested_translation"] = project_ru[key]
        previous = records_by_id.get(logical_id)
        if previous is not None:
            if (
                previous["source"] != source
                or previous["translation_key"] != key
            ):
                raise ValueError(f"conflicting Patchouli language key {key}")
            previous.setdefault("additional_locations", []).append(location)
        else:
            records_by_id[logical_id] = record

    for archive, nested_archive, member, data in documents:
        match = PATCHOULI_ASSET_RE.fullmatch(member)
        assert match is not None
        namespace, book, relative = match.groups()
        source_document = json.loads(data.decode("utf-8-sig"))
        output_member = (
            f"assets/{namespace}/patchouli_books/{book}/ru_ru/{relative}"
        )
        native_document = native_documents.get(
            (archive, nested_archive, output_member)
        )
        source_label = archive.relative_to(instance_root).as_posix()
        if nested_archive is not None:
            source_label += f"!/{nested_archive}"
        source_label += f"!/{member}"
        source_files[source_label] = sha256_bytes(data)
        for pointer, text in iter_json_strings(source_document):
            if text.startswith("#"):
                continue
            location = {
                "archive": archive.relative_to(instance_root).as_posix(),
                "member": member,
                "output_member": output_member,
                "pointer": pointer,
            }
            if nested_archive is not None:
                location["nested_archive"] = nested_archive
            if text in en_us:
                add_language_record(
                    namespace,
                    text,
                    en_us[text],
                    location,
                    "patchouli_language_text",
                )
                continue
            native_present = False
            if native_document is not None:
                try:
                    native_present = bool(json_pointer_get(native_document, pointer))
                except (KeyError, IndexError, TypeError, ValueError):
                    native_present = False
            logical_id = (
                f"patchouli-json:{namespace}:{book}:{relative}:{pointer}"
            )
            record = {
                "id": logical_id,
                "kind": "patchouli_json_text",
                "source": text,
                "source_hash": source_hash(text),
                "namespace": namespace,
                "native_ru_present": native_present,
                "output_format": "patchouli_json",
                "location": location,
            }
            previous = records_by_id.get(logical_id)
            if previous is not None and (
                previous["source"] != text
                or previous["location"] != location
            ):
                raise ValueError(f"conflicting Patchouli JSON field {logical_id}")
            records_by_id[logical_id] = record

    for archive, nested_archive, member, data in book_documents:
        match = PATCHOULI_BOOK_RE.fullmatch(member)
        assert match is not None
        namespace, book = match.groups()
        source_document = json.loads(data.decode("utf-8-sig"))
        source_label = archive.relative_to(instance_root).as_posix()
        if nested_archive is not None:
            source_label += f"!/{nested_archive}"
        source_label += f"!/{member}"
        source_files[source_label] = sha256_bytes(data)
        for pointer, text in iter_json_strings(source_document):
            add_language_record(
                namespace,
                text,
                en_us.get(text, text),
                {
                    "archive": archive.relative_to(instance_root).as_posix(),
                    "member": member,
                    "book": book,
                    "pointer": pointer,
                    **(
                        {"nested_archive": nested_archive}
                        if nested_archive is not None
                        else {}
                    ),
                },
                "patchouli_book_metadata",
            )

    records = sorted(records_by_id.values(), key=lambda record: record["id"])
    return SourceResult(
        records=records,
        source_files=[
            {"path": path, "sha256": digest}
            for path, digest in sorted(source_files.items())
        ],
        report={
            "books": len(
                {
                    (
                        record["namespace"],
                        record["location"].get("book")
                        or PATCHOULI_ASSET_RE.fullmatch(
                            record["location"]["member"]
                        ).group(2),
                    )
                    for record in records
                }
            ),
            "native_ru": sum(record["native_ru_present"] for record in records),
            "language_records": sum(
                record["output_format"] == "lang" for record in records
            ),
            "json_records": sum(
                record["output_format"] == "patchouli_json"
                for record in records
            ),
        },
    )


def _archive_label(
    archive: Path,
    instance_root: Path,
    member: str,
) -> str:
    return f"{archive.relative_to(instance_root).as_posix()}!/{member}"


def _manual_marker_sequence(lines: list[str]) -> list[str]:
    markers: list[str] = []
    for line in lines:
        for token in re.findall(r"<[^>]+>", line):
            if token.startswith("<link;"):
                parts = token[1:-1].split(";")
                markers.append(
                    ";".join(
                        [parts[0], parts[1], "*", *parts[3:]]
                    )
                )
            else:
                markers.append(token)
    return markers


def _manual_structure(lines: list[str]) -> list[list[str]]:
    return [_manual_marker_sequence([line]) for line in lines]


def _is_manual_translatable(text: str) -> bool:
    stripped = text.strip()
    if not stripped:
        return False
    if re.fullmatch(r"(?:<[^>]+>\s*)+", stripped):
        return "<link;" in stripped
    return True


def _mantle_structure(document: Any) -> Any:
    normalized = deepcopy(document)
    for pointer, _ in iter_mantle_strings(document):
        json_pointer_set(normalized, pointer, "<translated>")
    return normalized


def collect_immersive_engineering_manual(
    definition: SourceDefinition,
    archives: list[Path],
    instance_root: Path,
) -> SourceResult:
    namespace_filter = definition.options.get(
        "namespace", "immersiveengineering"
    )
    review_native = bool(definition.options.get("review_native", False))
    records: list[dict[str, Any]] = []
    source_files: dict[str, str] = {}
    articles = 0
    missing_native = 0
    stale_native = 0

    for archive in archives:
        try:
            with zipfile.ZipFile(archive) as jar:
                names = set(jar.namelist())
                for member in sorted(names):
                    match = IE_MANUAL_RE.fullmatch(member)
                    if match is None:
                        continue
                    namespace, relative = match.groups()
                    if namespace != namespace_filter:
                        continue
                    articles += 1
                    data = jar.read(member)
                    source_lines = data.decode("utf-8-sig").splitlines()
                    output_member = member.replace("/en_us/", "/ru_ru/", 1)
                    native_data = (
                        jar.read(output_member)
                        if output_member in names
                        else None
                    )
                    native_lines = (
                        native_data.decode("utf-8-sig").splitlines()
                        if native_data is not None
                        else []
                    )
                    if native_data is None:
                        file_status = "MISSING_NATIVE"
                        missing_native += 1
                    elif (
                        len(source_lines) != len(native_lines)
                        or _manual_structure(source_lines)
                        != _manual_structure(native_lines)
                    ):
                        file_status = "STALE_NATIVE"
                        stale_native += 1
                    else:
                        file_status = "REVIEW_NATIVE"
                    source_files[
                        _archive_label(archive, instance_root, member)
                    ] = sha256_bytes(data)
                    if native_data is not None:
                        source_files[
                            _archive_label(
                                archive, instance_root, output_member
                            )
                        ] = sha256_bytes(native_data)
                    for index, text in enumerate(source_lines):
                        if not _is_manual_translatable(text):
                            continue
                        native_translation = (
                            native_lines[index]
                            if file_status == "REVIEW_NATIVE"
                            and native_lines[index].strip()
                            else None
                        )
                        record = {
                            "id": (
                                f"ie-manual:{namespace}:{relative}:line:{index}"
                            ),
                            "kind": "manual_text",
                            "source": text,
                            "source_hash": source_hash(text),
                            "namespace": namespace,
                            "native_ru_present": native_translation is not None,
                            "native_translation_status": file_status,
                            "review_native": review_native,
                            "force_output": review_native,
                            "output_format": "manual_text",
                            "location": {
                                "archive": archive.relative_to(
                                    instance_root
                                ).as_posix(),
                                "member": member,
                                "output_member": output_member,
                                "line": index,
                            },
                        }
                        if native_translation is not None:
                            record["native_translation"] = native_translation
                            record["suggested_translation"] = native_translation
                        records.append(record)
        except zipfile.BadZipFile:
            continue

    return SourceResult(
        records=records,
        source_files=[
            {"path": path, "sha256": digest}
            for path, digest in sorted(source_files.items())
        ],
        report={
            "articles": articles,
            "missing_native_articles": missing_native,
            "stale_native_articles": stale_native,
            "native_review_records": len(records),
        },
    )


def collect_mantle_books(
    definition: SourceDefinition,
    archives: list[Path],
    instance_root: Path,
) -> SourceResult:
    namespace_filter = definition.options.get("namespace")
    excluded_books = set(definition.options.get("exclude_books", []))
    review_native = bool(definition.options.get("review_native", False))
    records: list[dict[str, Any]] = []
    source_files: dict[str, str] = {}
    books: set[tuple[str, str]] = set()
    missing_native_pages = 0
    invalid_native_pages = 0
    stale_native_pages = 0

    for archive in archives:
        try:
            with zipfile.ZipFile(archive) as jar:
                names = set(jar.namelist())
                for member in sorted(names):
                    match = MANTLE_BOOK_RE.fullmatch(member)
                    if match is None:
                        continue
                    namespace, book, relative = match.groups()
                    if (
                        namespace_filter is not None
                        and namespace != namespace_filter
                    ) or book in excluded_books:
                        continue
                    books.add((namespace, book))
                    data = jar.read(member)
                    source_document = json.loads(data.decode("utf-8-sig"))
                    output_member = member.replace("/en_us/", "/ru_ru/", 1)
                    native_data = (
                        jar.read(output_member)
                        if output_member in names
                        else None
                    )
                    native_document = None
                    if native_data is None:
                        file_status = "MISSING_NATIVE"
                        missing_native_pages += 1
                    else:
                        try:
                            native_document = json.loads(
                                native_data.decode("utf-8-sig")
                            )
                            if _mantle_structure(
                                source_document
                            ) == _mantle_structure(native_document):
                                file_status = "REVIEW_NATIVE"
                            else:
                                file_status = "STALE_NATIVE"
                                stale_native_pages += 1
                        except json.JSONDecodeError:
                            file_status = "INVALID_NATIVE"
                            invalid_native_pages += 1
                    source_files[
                        _archive_label(archive, instance_root, member)
                    ] = sha256_bytes(data)
                    if native_data is not None:
                        source_files[
                            _archive_label(
                                archive, instance_root, output_member
                            )
                        ] = sha256_bytes(native_data)

                    for pointer, text in iter_mantle_strings(source_document):
                        native_translation = None
                        if (
                            file_status == "REVIEW_NATIVE"
                            and native_document is not None
                        ):
                            try:
                                value = json_pointer_get(
                                    native_document, pointer
                                )
                                if isinstance(value, str) and value.strip():
                                    native_translation = value
                            except (KeyError, IndexError, TypeError, ValueError):
                                raise AssertionError(
                                    "matching Mantle structures must expose "
                                    f"translation pointer {pointer}"
                                )
                        record = {
                            "id": (
                                f"mantle-book:{namespace}:{book}:"
                                f"{relative}:{pointer}"
                            ),
                            "kind": "mantle_book_text",
                            "source": text,
                            "source_hash": source_hash(text),
                            "namespace": namespace,
                            "native_ru_present": native_translation
                            is not None,
                            "native_translation_status": file_status,
                            "review_native": review_native,
                            "force_output": review_native,
                            "output_format": "mantle_book_json",
                            "location": {
                                "archive": archive.relative_to(
                                    instance_root
                                ).as_posix(),
                                "member": member,
                                "output_member": output_member,
                                "pointer": pointer,
                                "book": book,
                            },
                        }
                        if native_translation is not None:
                            record["native_translation"] = native_translation
                            record["suggested_translation"] = native_translation
                        records.append(record)
        except zipfile.BadZipFile:
            continue

    return SourceResult(
        records=records,
        source_files=[
            {"path": path, "sha256": digest}
            for path, digest in sorted(source_files.items())
        ],
        report={
            "books": len(books),
            "missing_native_pages": missing_native_pages,
            "invalid_native_pages": invalid_native_pages,
            "stale_native_pages": stale_native_pages,
            "native_review_records": len(records),
        },
    )
