#!/usr/bin/env python3
"""Build quest and item resources by walking one version manifest."""

from __future__ import annotations

import argparse
import io
import json
import shutil
import tempfile
import zipfile
from collections import Counter, defaultdict
from copy import deepcopy
from pathlib import Path
from typing import Any

from catalog_utils import SnbtParser, sha256_bytes, validate_catalog
from quest_sources import extract_records
from runtime_audit_overrides import load_runtime_audit_overrides
from translation_sources import json_pointer_set, parse_mantle_language


def json_bytes(value: Any) -> bytes:
    return (json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n").encode(
        "utf-8"
    )


def optional_catalog_translation(
    catalog: dict[str, Any], record: dict[str, Any]
) -> str | None:
    matches = [
        variant["translation"]
        for variant in catalog["entries"].get(record["id"], [])
        if variant["source_hash"] == record["source_hash"]
        and variant["source"] == record["source"]
    ]
    if len(matches) > 1:
        raise ValueError(f"{record['id']}: expected one exact catalog translation")
    return matches[0] if matches else None


def catalog_translation(catalog: dict[str, Any], record: dict[str, Any]) -> str:
    translation = optional_catalog_translation(catalog, record)
    if translation is None:
        raise ValueError(f"{record['id']}: expected one exact catalog translation")
    return translation


def catalog_has(catalog: dict[str, Any], record: dict[str, Any]) -> bool:
    return optional_catalog_translation(catalog, record) is not None


def record_needs_catalog(record: dict[str, Any]) -> bool:
    status = record.get("translation_status")
    if status is not None:
        if status == "NATIVE_RU":
            return False
        return not (
            status == "FINALIZED"
            and record.get("effective_translation_origin")
            in {"native_ru", "translation-overrides"}
        )
    return not record.get("native_ru_present", False)


def unresolved_translation_ids(
    manifest: dict[str, Any], catalog: dict[str, Any]
) -> list[str]:
    return [
        record["id"]
        for record in manifest["records"]
        if (
            record.get("translation_status")
            not in {None, "FINALIZED", "NATIVE_RU"}
            or (
                record_needs_catalog(record)
                and not catalog_has(catalog, record)
            )
        )
    ]


def mask_visible_fields(value: Any) -> Any:
    if isinstance(value, dict):
        masked = {}
        for key, child in value.items():
            if key in {"title", "subtitle"} and isinstance(child, str):
                masked[key] = "<translated>"
            elif key == "description" and isinstance(child, list):
                masked[key] = [
                    "<translated>" if isinstance(entry, str) and entry else entry
                    for entry in child
                ]
            else:
                masked[key] = mask_visible_fields(child)
        return masked
    if isinstance(value, list):
        return [mask_visible_fields(child) for child in value]
    return value


def overlay_json(base: Any, localized: Any) -> Any:
    if isinstance(base, dict) and isinstance(localized, dict):
        result = deepcopy(base)
        for key, value in localized.items():
            result[key] = (
                overlay_json(result[key], value)
                if key in result
                else deepcopy(value)
            )
        return result
    if isinstance(base, list) and isinstance(localized, list):
        result = deepcopy(base)
        for index, value in enumerate(localized):
            if index < len(result):
                result[index] = overlay_json(result[index], value)
            else:
                result.append(deepcopy(value))
        return result
    return deepcopy(localized)


def build_quests(
    manifest: dict[str, Any],
    catalog: dict[str, Any],
    instance_root: Path,
    output: Path,
) -> int:
    source_root = instance_root / "config/ftbquests/quests"
    quest_records = [
        record
        for record in manifest["records"]
        if record.get("source_type") == "ftb_quests"
    ]
    by_file: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for record in quest_records:
        by_file[record["location"]["file"]].append(record)

    quest_sources = {
        entry["path"].removeprefix("config/ftbquests/quests/"): entry["sha256"]
        for entry in manifest["source_files"]
        if entry["path"].startswith("config/ftbquests/quests/")
    }
    if output.exists():
        shutil.rmtree(output)
    output.mkdir(parents=True)

    translated = 0
    for relative, expected_hash in sorted(quest_sources.items()):
        source_path = source_root / relative
        if sha256_bytes(source_path.read_bytes()) != expected_hash:
            raise ValueError(f"{source_path}: source hash changed")
        source = source_path.read_text(encoding="utf-8")
        records = by_file.get(relative, [])
        expected = Counter(
            (record["location"]["field"], record["source"]) for record in records
        )
        extracted = [
            record
            for record in extract_records(source)
            if record["original"]
        ]
        actual = Counter((record["field"], record["original"]) for record in extracted)
        if actual != expected:
            raise ValueError(f"{relative}: extracted quest fields differ from manifest")

        translations: dict[str, set[str]] = defaultdict(set)
        for record in records:
            translations[record["source"]].add(catalog_translation(catalog, record))
        ambiguous = [source for source, values in translations.items() if len(values) != 1]
        if ambiguous:
            raise ValueError(f"{relative}: conflicting translations for {ambiguous[:3]}")

        updated = source
        replacements = [
            (
                record["start"],
                record["end"],
                json.dumps(
                    next(iter(translations[record["original"]])),
                    ensure_ascii=False,
                ),
            )
            for record in extracted
        ]
        for start, end, replacement in sorted(replacements, reverse=True):
            updated = updated[:start] + replacement + updated[end:]
        if mask_visible_fields(SnbtParser(source).parse()) != mask_visible_fields(
            SnbtParser(updated).parse()
        ):
            raise ValueError(f"{relative}: non-translatable SNBT structure changed")
        target = output / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(updated, encoding="utf-8")
        translated += len(replacements)
    return translated


def build_language_files(
    manifest: dict[str, Any],
    catalog: dict[str, Any],
    runtime_overrides: dict[str, dict[str, str]],
    output: Path,
) -> int:
    assets = output / "assets"
    if assets.exists():
        shutil.rmtree(assets)
    by_namespace: dict[str, dict[str, str]] = defaultdict(dict)
    for record in manifest["records"]:
        if record.get("output_format") != "lang":
            continue
        value = optional_catalog_translation(catalog, record)
        if (
            record.get("native_ru_present", False)
            and not record.get("force_output", False)
            and (
                value is None
                or value == record.get("native_translation")
                or (
                    "native_translation" not in record
                    and (
                        not record.get("native_ru_same_as_source", False)
                        or value == record["source"]
                    )
                )
            )
        ):
            continue
        if value is None:
            value = catalog_translation(catalog, record)
        keys = [record["translation_key"], *record.get("translation_aliases", [])]
        for key in keys:
            if key in runtime_overrides.get(record["namespace"], {}):
                continue
            previous = by_namespace[record["namespace"]].get(key)
            if previous is not None and previous != value:
                raise ValueError(f"{key}: conflicting output translations")
            by_namespace[record["namespace"]][key] = value

    for namespace, values in sorted(runtime_overrides.items()):
        for key, value in sorted(values.items()):
            previous = by_namespace[namespace].get(key)
            if previous is not None and previous != value:
                raise ValueError(f"{key}: runtime audit override conflicts with catalog")
            by_namespace[namespace][key] = value

    for namespace, values in sorted(by_namespace.items()):
        target = assets / namespace / "lang/ru_ru.json"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(json_bytes(values))
    return sum(len(values) for values in by_namespace.values())


def build_patchouli_files(
    manifest: dict[str, Any],
    catalog: dict[str, Any],
    instance_root: Path,
    output: Path,
) -> int:
    grouped: dict[
        tuple[str, str | None, str, str], list[dict[str, Any]]
    ] = defaultdict(list)
    for record in manifest["records"]:
        if (
            record.get("output_format") == "patchouli_json"
            and not record["native_ru_present"]
        ):
            location = record["location"]
            grouped[
                (
                    location["archive"],
                    location.get("nested_archive"),
                    location["member"],
                    location["output_member"],
                )
            ].append(record)

    translated = 0
    for (
        archive_name,
        nested_archive,
        member,
        output_member,
    ), records in sorted(
        grouped.items(),
        key=lambda item: tuple(value or "" for value in item[0]),
    ):
        archive = instance_root / archive_name
        with zipfile.ZipFile(archive) as jar:
            if nested_archive is None:
                container = jar
                source_data = container.read(member)
                native_member = member.replace("/en_us/", "/ru_ru/", 1)
                native_data = (
                    container.read(native_member)
                    if native_member in container.namelist()
                    else None
                )
            else:
                with zipfile.ZipFile(
                    io.BytesIO(jar.read(nested_archive))
                ) as nested:
                    source_data = nested.read(member)
                    native_member = member.replace("/en_us/", "/ru_ru/", 1)
                    native_data = (
                        nested.read(native_member)
                        if native_member in nested.namelist()
                        else None
                    )
        source_label = archive_name
        if nested_archive is not None:
            source_label += f"!/{nested_archive}"
        source_label += f"!/{member}"
        expected_hash = next(
            (
                entry["sha256"]
                for entry in manifest["source_files"]
                if entry["path"] == source_label
            ),
            None,
        )
        if expected_hash is None or sha256_bytes(source_data) != expected_hash:
            raise ValueError(f"{source_label}: source hash changed")
        source_document = json.loads(source_data.decode("utf-8-sig"))
        translated_document = (
            overlay_json(
                source_document,
                json.loads(native_data.decode("utf-8-sig")),
            )
            if native_data is not None
            else deepcopy(source_document)
        )
        for record in records:
            json_pointer_set(
                translated_document,
                record["location"]["pointer"],
                catalog_translation(catalog, record),
            )
            translated += 1
        target = output / output_member
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(json_bytes(translated_document))
    return translated


def _verified_archive_member(
    manifest: dict[str, Any],
    instance_root: Path,
    archive_name: str,
    member: str,
) -> bytes:
    archive = instance_root / archive_name
    with zipfile.ZipFile(archive) as jar:
        data = jar.read(member)
    source_label = f"{archive_name}!/{member}"
    expected_hash = next(
        (
            entry["sha256"]
            for entry in manifest["source_files"]
            if entry["path"] == source_label
        ),
        None,
    )
    if expected_hash is None or sha256_bytes(data) != expected_hash:
        raise ValueError(f"{source_label}: source hash changed")
    return data


def build_manual_files(
    manifest: dict[str, Any],
    catalog: dict[str, Any],
    instance_root: Path,
    output: Path,
) -> int:
    grouped: dict[
        tuple[str, str, str], list[dict[str, Any]]
    ] = defaultdict(list)
    for record in manifest["records"]:
        if (
            record.get("output_format") == "manual_text"
            and record.get("force_output", False)
        ):
            location = record["location"]
            grouped[
                (
                    location["archive"],
                    location["member"],
                    location["output_member"],
                )
            ].append(record)

    translated = 0
    for (archive_name, member, output_member), records in sorted(
        grouped.items()
    ):
        data = _verified_archive_member(
            manifest, instance_root, archive_name, member
        )
        lines = data.decode("utf-8-sig").splitlines()
        for record in records:
            lines[record["location"]["line"]] = catalog_translation(
                catalog, record
            )
            translated += 1
        target = output / output_member
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text("\n".join(lines), encoding="utf-8")
    return translated


def build_mantle_book_files(
    manifest: dict[str, Any],
    catalog: dict[str, Any],
    instance_root: Path,
    output: Path,
) -> int:
    grouped: dict[
        tuple[str, str, str], list[dict[str, Any]]
    ] = defaultdict(list)
    for record in manifest["records"]:
        if (
            record.get("output_format") == "mantle_book_json"
            and record.get("force_output", False)
        ):
            location = record["location"]
            grouped[
                (
                    location["archive"],
                    location["member"],
                    location["output_member"],
                )
            ].append(record)

    translated = 0
    for (archive_name, member, output_member), records in sorted(
        grouped.items()
    ):
        data = _verified_archive_member(
            manifest, instance_root, archive_name, member
        )
        document = json.loads(data.decode("utf-8-sig"))
        for record in records:
            json_pointer_set(
                document,
                record["location"]["pointer"],
                catalog_translation(catalog, record),
            )
            translated += 1
        target = output / output_member
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(json_bytes(document))
    return translated


def build_mantle_book_language_files(
    manifest: dict[str, Any],
    catalog: dict[str, Any],
    instance_root: Path,
    output: Path,
) -> int:
    grouped: dict[
        tuple[str, str, str], list[dict[str, Any]]
    ] = defaultdict(list)
    for record in manifest["records"]:
        if (
            record.get("output_format") == "mantle_book_language"
            and record.get("force_output", False)
        ):
            location = record["location"]
            grouped[
                (
                    location["archive"],
                    location["member"],
                    location["output_member"],
                )
            ].append(record)

    translated = 0
    for (archive_name, member, output_member), records in sorted(
        grouped.items()
    ):
        source_data = _verified_archive_member(
            manifest, instance_root, archive_name, member
        )
        native_member = member.replace("/en_us/", "/ru_ru/", 1)
        archive = instance_root / archive_name
        with zipfile.ZipFile(archive) as jar:
            native_data = (
                jar.read(native_member)
                if native_member in jar.namelist()
                else None
            )
        if native_data is not None:
            source_label = f"{archive_name}!/{native_member}"
            expected_hash = next(
                (
                    entry["sha256"]
                    for entry in manifest["source_files"]
                    if entry["path"] == source_label
                ),
                None,
            )
            if (
                expected_hash is None
                or sha256_bytes(native_data) != expected_hash
            ):
                raise ValueError(f"{source_label}: source hash changed")

        values = (
            parse_mantle_language(native_data, native_member)
            if native_data is not None
            else {}
        )
        source_values = parse_mantle_language(source_data, member)
        for record in records:
            key = record["location"]["key"]
            if key not in source_values:
                raise ValueError(f"{member}: missing Mantle language key {key}")
            values[key] = catalog_translation(catalog, record)
            translated += 1
        target = output / output_member
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(
            "".join(f"{key}={value}\n" for key, value in values.items()),
            encoding="utf-8",
        )
    return translated


def snapshot(root: Path) -> dict[str, bytes]:
    return {
        path.relative_to(root).as_posix(): path.read_bytes()
        for path in sorted(root.rglob("*"))
        if path.is_file()
    }


def apply_resource_override(
    source: Path,
    relative: Path,
    resourcepack: Path,
) -> None:
    target = resourcepack / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    if (
        len(relative.parts) == 4
        and relative.parts[0] == "assets"
        and relative.parts[2] == "lang"
        and relative.suffix == ".json"
        and target.exists()
    ):
        generated = json.loads(target.read_text(encoding="utf-8"))
        override = json.loads(source.read_text(encoding="utf-8"))
        if not isinstance(generated, dict) or not isinstance(override, dict):
            raise ValueError(f"{relative}: language files must be JSON objects")
        generated.update(override)
        target.write_bytes(json_bytes(generated))
    else:
        shutil.copy2(source, target)


def apply_translation_overrides(overrides: Path, resourcepack: Path) -> None:
    for source in sorted(overrides.rglob("*")):
        if not source.is_file():
            continue
        relative = source.relative_to(overrides)
        if (
            not relative.parts
            or relative.parts[0] != "assets"
            or ("ru_ru" not in relative.parts and source.stem != "ru_ru")
        ):
            raise ValueError(
                f"{relative}: translation overrides may only contain ru_ru resources"
            )
        apply_resource_override(source, relative, resourcepack)


def apply_compatibility_translations(
    compatibility_pack: Path,
    resourcepack: Path,
) -> int:
    copied = 0
    for source in sorted((compatibility_pack / "assets").rglob("*")):
        if not source.is_file():
            continue
        relative = source.relative_to(compatibility_pack)
        if "ru_ru" not in relative.parts and source.stem != "ru_ru":
            continue
        apply_resource_override(source, relative, resourcepack)
        copied += 1
    return copied


def parse_args() -> argparse.Namespace:
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", required=True)
    parser.add_argument("--instance-root", type=Path, required=True)
    parser.add_argument(
        "--catalog", type=Path, default=root / "translation-catalog/catalog.json"
    )
    parser.add_argument(
        "--versions-root", type=Path, default=root / "translation-versions"
    )
    parser.add_argument(
        "--output",
        type=Path,
        help="output payload directory (default: selected version's payload/)",
    )
    parser.add_argument("--check", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    version_root = args.versions_root.resolve() / args.version
    manifest = json.loads((version_root / "manifest.json").read_text(encoding="utf-8"))
    if manifest["version"] != args.version:
        raise ValueError("manifest version does not match requested version")
    source_config = version_root / manifest["source_config"]
    if sha256_bytes(source_config.read_bytes()) != manifest["source_config_hash"]:
        raise ValueError("translation source configuration changed")
    catalog = json.loads(args.catalog.read_text(encoding="utf-8"))
    validate_catalog(catalog)
    unresolved = unresolved_translation_ids(manifest, catalog)
    if unresolved:
        raise ValueError(
            f"manifest has {len(unresolved)} pending translations: {unresolved[:3]}"
        )
    runtime_policy = load_runtime_audit_overrides(
        version_root / "runtime-audit-overrides.json"
    )
    runtime_overrides = runtime_policy.translations

    destination = (
        args.output.resolve()
        if args.output is not None
        else version_root / "payload"
    )
    with tempfile.TemporaryDirectory(prefix="liminal-translation-") as temp:
        generated = Path(temp) / "src"
        resourcepack = generated / "resourcepack"
        resourcepack.mkdir(parents=True)
        quest_count = build_quests(
            manifest, catalog, args.instance_root.resolve(), generated / "quests"
        )
        language_count = build_language_files(
            manifest, catalog, runtime_overrides, resourcepack
        )
        patchouli_count = build_patchouli_files(
            manifest,
            catalog,
            args.instance_root.resolve(),
            resourcepack,
        )
        manual_count = build_manual_files(
            manifest,
            catalog,
            args.instance_root.resolve(),
            resourcepack,
        )
        mantle_book_count = build_mantle_book_files(
            manifest,
            catalog,
            args.instance_root.resolve(),
            resourcepack,
        )
        mantle_language_count = build_mantle_book_language_files(
            manifest,
            catalog,
            args.instance_root.resolve(),
            resourcepack,
        )
        compatibility_pack = version_root / "compatibility-resourcepack"
        compatibility_translation_count = (
            apply_compatibility_translations(
                compatibility_pack,
                resourcepack,
            )
            if compatibility_pack.exists()
            else 0
        )
        translation_overrides = version_root / "translation-overrides"
        if translation_overrides.exists():
            apply_translation_overrides(
                translation_overrides,
                resourcepack,
            )
        if args.check:
            if snapshot(generated) != snapshot(destination):
                raise ValueError("generated resources are not current")
        else:
            if destination.exists():
                shutil.rmtree(destination)
            shutil.copytree(generated, destination)

    report = {
        "version": args.version,
        "manifest_records": len(manifest["records"]),
        "catalog_hits": sum(
            catalog_has(catalog, record)
            for record in manifest["records"]
        ),
        "native_ru_coverage": sum(
            not record_needs_catalog(record)
            for record in manifest["records"]
        ),
        "pending": 0,
        "errors": 0,
        "output_quest_strings": quest_count,
        "output_language_translation_keys": language_count,
        "output_patchouli_strings": patchouli_count,
        "output_manual_strings": manual_count,
        "output_mantle_book_strings": mantle_book_count,
        "output_mantle_book_language_strings": mantle_language_count,
        "compatibility_translation_resources": compatibility_translation_count,
        "runtime_audit_translation_keys": sum(
            len(values) for values in runtime_overrides.values()
        ),
    }
    report_path = version_root / "build-report.json"
    if args.check:
        if not report_path.exists() or json.loads(
            report_path.read_text(encoding="utf-8")
        ) != report:
            raise ValueError("build report is not current")
        print(f"Resources are current for {args.version}")
    else:
        report_path.write_bytes(json_bytes(report))
        print(
            f"Built {quest_count} quest strings, {language_count} language keys, "
            f"{patchouli_count} Patchouli strings, {manual_count} manual strings, "
            f"{mantle_book_count} Mantle book strings, and "
            f"{mantle_language_count} Mantle language strings "
            f"for {args.version}"
        )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as exc:
        print(f"error: {exc}")
        raise SystemExit(2)
