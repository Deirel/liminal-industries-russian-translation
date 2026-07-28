#!/usr/bin/env python3
"""Build the immutable initial translation catalog and its version manifest."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
import sys
import zipfile
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from extract_item_names import (
    candidate_keys,
    load_jar_languages,
    load_minecraft_languages,
    load_probe_item_ids,
    overlay_loose_languages,
)


SCHEMA_VERSION = 1
VERSION_SLUG = "1.19.3-7-ae2-fix"
EXPECTED = {
    "modpack_name": "Liminal Industries - Rescripted",
    "modpack_version": "1.19.3 - 7 (ae2 fix)",
    "curseforge_file_id": 8509377,
    "minecraft_version": "1.20.1",
    "forge_version": "47.4.13",
    "ftb_quests_version": "2001.4.22",
}
TECHNICAL_TOKEN_RE = re.compile(
    r"(?:[&§][0-9A-FK-ORa-fk-or]|%(?:\d+\$)?[sdif]|\{image:[^}]+\})"
)


def sha256_bytes(data: bytes) -> str:
    return "sha256:" + hashlib.sha256(data).hexdigest()


def source_hash(source: str) -> str:
    return sha256_bytes(source.encode("utf-8"))


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def json_bytes(value: Any) -> bytes:
    return (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


@dataclass
class SnbtParser:
    text: str
    pos: int = 0

    def error(self, message: str) -> ValueError:
        return ValueError(f"{message} at character {self.pos}")

    def skip_space(self) -> None:
        while self.pos < len(self.text):
            if self.text[self.pos].isspace() or self.text[self.pos] == ",":
                self.pos += 1
                continue
            if self.text.startswith("//", self.pos):
                end = self.text.find("\n", self.pos)
                self.pos = len(self.text) if end < 0 else end + 1
                continue
            if self.text[self.pos] == "#":
                end = self.text.find("\n", self.pos)
                self.pos = len(self.text) if end < 0 else end + 1
                continue
            break

    def parse(self) -> Any:
        value = self.parse_value()
        self.skip_space()
        if self.pos != len(self.text):
            raise self.error("unexpected trailing SNBT")
        return value

    def parse_value(self) -> Any:
        self.skip_space()
        if self.pos >= len(self.text):
            raise self.error("expected SNBT value")
        char = self.text[self.pos]
        if char == "{":
            return self.parse_compound()
        if char == "[":
            return self.parse_list()
        if char in "\"'":
            return self.parse_string()
        return self.parse_bare()

    def parse_string(self) -> str:
        quote = self.text[self.pos]
        if quote == '"':
            start = self.pos
            self.pos += 1
            escaped = False
            while self.pos < len(self.text):
                char = self.text[self.pos]
                self.pos += 1
                if escaped:
                    escaped = False
                elif char == "\\":
                    escaped = True
                elif char == quote:
                    return json.loads(self.text[start : self.pos])
            raise self.error("unterminated SNBT string")

        self.pos += 1
        result: list[str] = []
        escaped = False
        while self.pos < len(self.text):
            char = self.text[self.pos]
            self.pos += 1
            if escaped:
                result.append(char)
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == quote:
                return "".join(result)
            else:
                result.append(char)
        raise self.error("unterminated SNBT string")

    def parse_bare(self) -> str:
        start = self.pos
        while self.pos < len(self.text):
            char = self.text[self.pos]
            if char.isspace() or char in ",]}":
                break
            self.pos += 1
        if self.pos == start:
            raise self.error("expected bare SNBT value")
        return self.text[start : self.pos]

    def parse_key(self) -> str:
        self.skip_space()
        if self.pos < len(self.text) and self.text[self.pos] in "\"'":
            return self.parse_string()
        start = self.pos
        while self.pos < len(self.text):
            char = self.text[self.pos]
            if char == ":" or char.isspace():
                break
            self.pos += 1
        if self.pos == start:
            raise self.error("expected compound key")
        return self.text[start : self.pos]

    def parse_compound(self) -> dict[str, Any]:
        result: dict[str, Any] = {}
        self.pos += 1
        while True:
            self.skip_space()
            if self.pos >= len(self.text):
                raise self.error("unterminated compound")
            if self.text[self.pos] == "}":
                self.pos += 1
                return result
            key = self.parse_key()
            self.skip_space()
            if self.pos >= len(self.text) or self.text[self.pos] != ":":
                raise self.error(f"expected ':' after {key!r}")
            self.pos += 1
            if key in result:
                raise self.error(f"duplicate compound key {key!r}")
            result[key] = self.parse_value()

    def parse_list(self) -> list[Any]:
        result: list[Any] = []
        self.pos += 1
        self.skip_space()
        # Typed arrays are not used by the translated fields, but accepting the
        # prefix keeps the parser useful for otherwise valid quest SNBT.
        if (
            self.pos + 1 < len(self.text)
            and self.text[self.pos] in "BIL"
            and self.text[self.pos + 1] == ";"
        ):
            self.pos += 2
        while True:
            self.skip_space()
            if self.pos >= len(self.text):
                raise self.error("unterminated list")
            if self.text[self.pos] == "]":
                self.pos += 1
                return result
            result.append(self.parse_value())


def parse_snbt(path: Path) -> dict[str, Any]:
    parsed = SnbtParser(path.read_text(encoding="utf-8")).parse()
    if not isinstance(parsed, dict):
        raise ValueError(f"{path}: root must be a compound")
    return parsed


def require_string(owner: dict[str, Any], key: str, context: str) -> str:
    value = owner.get(key)
    if not isinstance(value, str) or not value:
        raise ValueError(f"{context}: missing non-empty string {key!r}")
    return value


def record(
    logical_id: str,
    kind: str,
    source: str,
    translation: str,
    location: dict[str, Any],
) -> tuple[dict[str, Any], dict[str, str]]:
    if not source or not translation:
        raise ValueError(f"{logical_id}: source and translation must be non-empty")
    source_tokens = sorted(TECHNICAL_TOKEN_RE.findall(source))
    translation_tokens = sorted(TECHNICAL_TOKEN_RE.findall(translation))
    if source_tokens != translation_tokens:
        raise ValueError(
            f"{logical_id}: formatting or substitution tokens differ: "
            f"{source_tokens} != {translation_tokens}"
        )
    manifest_record = {
        "id": logical_id,
        "kind": kind,
        "source": source,
        "source_hash": source_hash(source),
        "location": location,
    }
    catalog_record = {
        "source_hash": manifest_record["source_hash"],
        "source": source,
        "translation": translation,
    }
    return manifest_record, catalog_record


def compare_ids(
    english: dict[str, Any], russian: dict[str, Any], context: str
) -> str:
    english_id = require_string(english, "id", context)
    russian_id = require_string(russian, "id", context)
    if english_id != russian_id:
        raise ValueError(f"{context}: ID differs: {english_id} != {russian_id}")
    return english_id


def pair_by_id(
    english: Any, russian: Any, context: str
) -> list[tuple[dict[str, Any], dict[str, Any]]]:
    if not isinstance(english, list) or not isinstance(russian, list):
        raise ValueError(f"{context}: expected two lists")

    def indexed(values: list[Any], side: str) -> dict[str, dict[str, Any]]:
        result: dict[str, dict[str, Any]] = {}
        for index, value in enumerate(values):
            if not isinstance(value, dict):
                raise ValueError(f"{context}: {side}[{index}] is not a compound")
            value_id = require_string(value, "id", f"{context}: {side}[{index}]")
            if value_id in result:
                raise ValueError(f"{context}: duplicate {side} ID {value_id}")
            result[value_id] = value
        return result

    left, right = indexed(english, "English"), indexed(russian, "Russian")
    if left.keys() != right.keys():
        raise ValueError(f"{context}: English and Russian ID sets differ")
    return [(left[value_id], right[value_id]) for value_id in left]


def append_record(
    manifest_records: list[dict[str, Any]],
    catalog_entries: dict[str, list[dict[str, str]]],
    logical_id: str,
    kind: str,
    source: str,
    translation: str,
    location: dict[str, Any],
) -> None:
    manifest_record, catalog_record = record(
        logical_id, kind, source, translation, location
    )
    if logical_id in catalog_entries:
        raise ValueError(f"duplicate logical ID in initial version: {logical_id}")
    manifest_records.append(manifest_record)
    catalog_entries[logical_id] = [catalog_record]


def add_simple_field(
    english: dict[str, Any],
    russian: dict[str, Any],
    field: str,
    logical_id: str,
    kind: str,
    location: dict[str, Any],
    manifest_records: list[dict[str, Any]],
    catalog_entries: dict[str, list[dict[str, str]]],
) -> None:
    english_value, russian_value = english.get(field), russian.get(field)
    if english_value is None and russian_value is None:
        return
    if not isinstance(english_value, str) or not isinstance(russian_value, str):
        raise ValueError(f"{logical_id}: {field} must be a string on both sides")
    if not english_value and not russian_value:
        return
    append_record(
        manifest_records,
        catalog_entries,
        logical_id,
        kind,
        english_value,
        russian_value,
        {**location, "field": field},
    )


def extract_quest_records(
    english_root: Path, russian_root: Path
) -> tuple[list[dict[str, Any]], dict[str, list[dict[str, str]]], list[dict[str, str]]]:
    manifest_records: list[dict[str, Any]] = []
    catalog_entries: dict[str, list[dict[str, str]]] = {}
    source_files: list[dict[str, str]] = []
    english_files = sorted(english_root.rglob("*.snbt"))
    russian_files = sorted(russian_root.rglob("*.snbt"))
    english_rel = {path.relative_to(english_root).as_posix() for path in english_files}
    russian_rel = {path.relative_to(russian_root).as_posix() for path in russian_files}
    if english_rel != russian_rel:
        raise ValueError("English and Russian quest file sets differ")

    for relative in sorted(english_rel):
        english_path, russian_path = english_root / relative, russian_root / relative
        source_files.append(
            {
                "path": f"data/config/ftbquests/quests/{relative}",
                "sha256": sha256_bytes(english_path.read_bytes()),
            }
        )
        english, russian = parse_snbt(english_path), parse_snbt(russian_path)
        base_location = {"file": relative}
        if relative == "chapter_groups.snbt":
            for left, right in pair_by_id(
                english.get("chapter_groups"),
                russian.get("chapter_groups"),
                relative,
            ):
                group_id = compare_ids(left, right, relative)
                add_simple_field(
                    left,
                    right,
                    "title",
                    f"quest-group:{group_id}:title",
                    "quest_group_title",
                    {**base_location, "group_id": group_id},
                    manifest_records,
                    catalog_entries,
                )
            continue
        if relative == "data.snbt":
            continue

        chapter_id = compare_ids(english, russian, relative)
        add_simple_field(
            english,
            russian,
            "title",
            f"quest-chapter:{chapter_id}:title",
            "quest_chapter_title",
            {**base_location, "chapter_id": chapter_id},
            manifest_records,
            catalog_entries,
        )
        for left, right in pair_by_id(
            english.get("quests"), russian.get("quests"), relative
        ):
            quest_id = compare_ids(left, right, relative)
            quest_location = {**base_location, "quest_id": quest_id}
            for field in ("title", "subtitle"):
                add_simple_field(
                    left,
                    right,
                    field,
                    f"quest:{quest_id}:{field}",
                    f"quest_{field}",
                    quest_location,
                    manifest_records,
                    catalog_entries,
                )

            english_description = left.get("description")
            russian_description = right.get("description")
            if english_description is not None or russian_description is not None:
                if not isinstance(english_description, list) or not isinstance(
                    russian_description, list
                ):
                    raise ValueError(
                        f"quest {quest_id}: description must be a list on both sides"
                    )
                if len(english_description) != len(russian_description):
                    raise ValueError(
                        f"quest {quest_id}: description lengths differ"
                    )
                for index, (source, translation) in enumerate(
                    zip(english_description, russian_description, strict=True)
                ):
                    if not isinstance(source, str) or not isinstance(translation, str):
                        raise ValueError(
                            f"quest {quest_id}: description[{index}] is not a string"
                        )
                    if not source and not translation:
                        continue
                    append_record(
                        manifest_records,
                        catalog_entries,
                        f"quest:{quest_id}:description:{index}",
                        "quest_description",
                        source,
                        translation,
                        {
                            **quest_location,
                            "field": "description",
                            "index": index,
                        },
                    )

            for container, prefix, kind in (
                ("tasks", "quest-task", "quest_task_title"),
                ("rewards", "quest-reward", "quest_reward_title"),
            ):
                english_children = left.get(container, [])
                russian_children = right.get(container, [])
                for child_left, child_right in pair_by_id(
                    english_children,
                    russian_children,
                    f"quest {quest_id} {container}",
                ):
                    child_id = compare_ids(child_left, child_right, relative)
                    add_simple_field(
                        child_left,
                        child_right,
                        "title",
                        f"{prefix}:{quest_id}:{child_id}:title",
                        kind,
                        {
                            **quest_location,
                            "container": container,
                            "entry_id": child_id,
                        },
                        manifest_records,
                        catalog_entries,
                    )

    manifest_records.sort(key=lambda item: item["id"])
    return manifest_records, catalog_entries, source_files


def load_project_translations(resource_pack: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    for path in sorted(resource_pack.glob("assets/*/lang/ru_ru.json")):
        data = read_json(path)
        if not isinstance(data, dict):
            raise ValueError(f"{path}: language file must contain an object")
        for key, value in data.items():
            if key in result:
                raise ValueError(f"duplicate project translation key: {key}")
            if not isinstance(value, str) or not value:
                raise ValueError(f"{path}: empty or non-string translation for {key}")
            result[key] = value
    return result


def load_reviewed_item_sources(work_root: Path) -> dict[str, tuple[str, str]]:
    result: dict[str, tuple[str, str]] = {}
    for path in sorted(work_root.glob("*_names_ru.tsv")):
        with path.open(encoding="utf-8", newline="") as handle:
            reader = csv.DictReader(handle, delimiter="\t")
            fields = set(reader.fieldnames or ())
            key_field = "translation_key" if "translation_key" in fields else "key"
            source_field = (
                "source_display_name" if "source_display_name" in fields else "en_us"
            )
            if key_field not in fields or source_field not in fields:
                continue
            for row_number, row in enumerate(reader, start=2):
                key, source = row[key_field], row[source_field]
                if not key or not source:
                    continue
                previous = result.get(key)
                if previous and previous[0] != source:
                    raise ValueError(
                        f"{path}:{row_number}: conflicting English source for {key}"
                    )
                result[key] = (source, path.name)
    return result


def fallback_runtime_name(item_id: str) -> str | None:
    namespace, path = item_id.split(":", 1)
    if namespace == "minecraft" and path.endswith("smithing_template"):
        return "Smithing Template"
    fixed = {
        "immersiveengineering:potion": "Potion",
        "neapolitan:magic_beans": "Magic Beans",
    }
    if item_id in fixed:
        return fixed[item_id]
    if namespace != "storagedrawers":
        return None

    match = re.fullmatch(
        r"(acacia|bamboo|birch|cherry|crimson|dark_oak|jungle|mangrove|oak|spruce|warped)"
        r"_(full|half)_drawers_([124])",
        path,
    )
    if match:
        wood, size, count = match.groups()
        material = wood.replace("_", " ").title()
        shape = {"1": "1x1", "2": "1x2", "4": "2x2"}[count]
        half = " Half" if size == "half" else ""
        return f"{material}{half} Drawers {shape}"
    match = re.fullmatch(
        r"(acacia|bamboo|birch|cherry|crimson|dark_oak|jungle|mangrove|oak|spruce|warped)_trim",
        path,
    )
    if match:
        return f"{match.group(1).replace('_', ' ').title()} Trim"
    return path.replace("_", " ").title()


def resolve_translation_key(
    item_id: str,
    en_us: dict[str, str],
    native_ru: dict[str, str],
    reviewed_sources: dict[str, tuple[str, str]],
    project_ru: dict[str, str],
) -> str:
    for key in candidate_keys(item_id):
        if (
            key in en_us
            or key in native_ru
            or key in reviewed_sources
            or key in project_ru
        ):
            return key
    return candidate_keys(item_id)[0]


def item_source_files(
    modpack_root: Path, minecraft_root: Path, work_root: Path
) -> list[dict[str, str]]:
    paths: list[tuple[Path, str]] = [
        (
            modpack_root / "data/.curseforge-manifest.json",
            "data/.curseforge-manifest.json",
        ),
        (modpack_root / "data/.forge-manifest.json", "data/.forge-manifest.json"),
    ]
    globals_path = modpack_root / "data/kubejs/probe/generated/globals.d.ts"
    paths.append((globals_path, "data/kubejs/probe/generated/globals.d.ts"))
    for path in sorted((modpack_root / "data/mods").glob("*.jar")):
        paths.append((path, f"data/mods/{path.name}"))
    for path in sorted((modpack_root / "data/kubejs").glob("assets/*/lang/*.json")):
        paths.append((path, f"data/kubejs/{path.relative_to(modpack_root / 'data/kubejs')}"))
    for path in sorted(
        (modpack_root / "data/kubejs").glob("contentpacks/*/assets/*/lang/*.json")
    ):
        paths.append((path, f"data/kubejs/{path.relative_to(modpack_root / 'data/kubejs')}"))
    minecraft_jar = minecraft_root / "versions/1.20.1/1.20.1.jar"
    if minecraft_jar.exists():
        paths.append((minecraft_jar, str(minecraft_jar)))
    for path in sorted(work_root.glob("*_names_ru.tsv")):
        paths.append((path, f"item-translation-work/{path.name}"))
    return [
        {"path": label, "sha256": sha256_bytes(path.read_bytes())}
        for path, label in paths
    ]


def extract_item_records(
    modpack_root: Path,
    minecraft_root: Path,
    resource_pack: Path,
    work_root: Path,
) -> tuple[
    list[dict[str, Any]],
    dict[str, list[dict[str, str]]],
    list[dict[str, str]],
    dict[str, Any],
]:
    en_us, native_ru = load_jar_languages(modpack_root / "data/mods")
    load_minecraft_languages(minecraft_root, en_us, native_ru)
    overlay_loose_languages(modpack_root / "data/kubejs", "en_us", en_us)
    overlay_loose_languages(modpack_root / "data/kubejs", "ru_ru", native_ru)
    registry_ids = load_probe_item_ids(modpack_root / "data/kubejs")
    if len(registry_ids) != 9322:
        raise ValueError(
            f"ProbeJS item registry has {len(registry_ids)} entries, expected 9322"
        )

    project_ru = load_project_translations(resource_pack)
    reviewed_sources = load_reviewed_item_sources(work_root)
    manifest_records: list[dict[str, Any]] = []
    catalog_entries: dict[str, list[dict[str, str]]] = {}
    by_key: dict[str, str] = {}
    source_origins: dict[str, int] = {}

    for item_id in sorted(registry_ids):
        key = resolve_translation_key(
            item_id, en_us, native_ru, reviewed_sources, project_ru
        )
        if key in by_key:
            raise ValueError(
                f"translation key {key} resolves for both {by_key[key]} and {item_id}"
            )
        by_key[key] = item_id
        if key in en_us:
            source, origin = en_us[key], "effective_en_us"
        elif key in reviewed_sources:
            source, filename = reviewed_sources[key]
            origin = f"reviewed_runtime_inventory:{filename}"
        else:
            source = fallback_runtime_name(item_id)
            origin = "runtime_generated"
        if not source:
            raise ValueError(f"no English source for {item_id} ({key})")
        source_origins[origin] = source_origins.get(origin, 0) + 1
        namespace = item_id.split(":", 1)[0]
        manifest_record = {
            "id": f"item:{key}",
            "kind": "item_name",
            "source": source,
            "source_hash": source_hash(source),
            "namespace": namespace,
            "item_id": item_id,
            "translation_key": key,
            "native_ru_present": key in native_ru,
            "source_origin": origin,
        }
        manifest_records.append(manifest_record)
        if key in project_ru:
            catalog_entries[manifest_record["id"]] = [
                {
                    "source_hash": manifest_record["source_hash"],
                    "source": source,
                    "translation": project_ru[key],
                }
            ]

    unmatched_project_keys = sorted(project_ru.keys() - by_key.keys())
    native_overrides = sorted(
        key for key in project_ru.keys() & by_key.keys() if key in native_ru
    )
    report = {
        "registry_items": len(registry_ids),
        "manifest_item_records": len(manifest_records),
        "catalog_item_records": len(catalog_entries),
        "source_origins": dict(sorted(source_origins.items())),
        "unmatched_project_keys": unmatched_project_keys,
        "native_ru_overrides": native_overrides,
    }
    return (
        manifest_records,
        catalog_entries,
        item_source_files(modpack_root, minecraft_root, work_root),
        report,
    )


def read_modpack_metadata(modpack_root: Path) -> dict[str, Any]:
    curseforge = read_json(modpack_root / "data/.curseforge-manifest.json")
    forge = read_json(modpack_root / "data/.forge-manifest.json")
    files = curseforge.get("files", [])
    ftb_match = next(
        (
            re.search(r"ftb-quests-forge-([0-9.]+)\.jar$", entry)
            for entry in files
            if "ftb-quests-forge-" in entry
        ),
        None,
    )
    if not ftb_match:
        raise ValueError("cannot determine FTB Quests version")
    actual = {
        "modpack_name": curseforge.get("modpackName"),
        "modpack_version": curseforge.get("modpackVersion"),
        "curseforge_file_id": curseforge.get("fileId"),
        "minecraft_version": forge.get("minecraftVersion"),
        "forge_version": forge.get("forgeVersion"),
        "ftb_quests_version": ftb_match.group(1),
    }
    if actual != EXPECTED:
        raise ValueError(f"wrong source version:\nexpected {EXPECTED}\nactual {actual}")
    return actual


def validate_catalog(catalog: dict[str, Any]) -> None:
    if catalog.get("schema_version") != SCHEMA_VERSION:
        raise ValueError("wrong catalog schema version")
    seen: set[tuple[str, str]] = set()
    for logical_id, variants in catalog.get("entries", {}).items():
        if not isinstance(variants, list) or not variants:
            raise ValueError(f"{logical_id}: variants must be a non-empty list")
        for variant in variants:
            source = variant.get("source")
            translation = variant.get("translation")
            digest = variant.get("source_hash")
            if not isinstance(source, str) or not source:
                raise ValueError(f"{logical_id}: empty source")
            if not isinstance(translation, str) or not translation:
                raise ValueError(f"{logical_id}: empty translation")
            if digest != source_hash(source):
                raise ValueError(f"{logical_id}: source hash mismatch")
            source_tokens = sorted(TECHNICAL_TOKEN_RE.findall(source))
            translation_tokens = sorted(TECHNICAL_TOKEN_RE.findall(translation))
            if source_tokens != translation_tokens:
                raise ValueError(f"{logical_id}: technical tokens differ")
            pair = (logical_id, digest)
            if pair in seen:
                raise ValueError(f"{logical_id}: duplicate ID + source_hash")
            seen.add(pair)


def build(args: argparse.Namespace) -> tuple[bytes, bytes, bytes]:
    metadata = read_modpack_metadata(args.modpack_root)
    quest_records, quest_catalog, quest_sources = extract_quest_records(
        args.modpack_root / "data/config/ftbquests/quests",
        args.translation_root
        / "translation-versions"
        / VERSION_SLUG
        / "payload/quests",
    )
    item_records, item_catalog, item_sources, item_report = extract_item_records(
        args.modpack_root,
        args.minecraft_root,
        args.translation_root
        / "translation-versions"
        / VERSION_SLUG
        / "payload/resourcepack",
        args.translation_root / "item-translation-work",
    )
    if len(quest_records) != 841:
        raise ValueError(
            f"quest extraction produced {len(quest_records)} records, expected 841"
        )
    overlap = quest_catalog.keys() & item_catalog.keys()
    if overlap:
        raise ValueError(f"catalog ID collision: {sorted(overlap)[:3]}")
    catalog = {
        "schema_version": SCHEMA_VERSION,
        "entries": dict(sorted({**quest_catalog, **item_catalog}.items())),
    }
    validate_catalog(catalog)
    manifest = {
        "schema_version": SCHEMA_VERSION,
        "version": VERSION_SLUG,
        "modpack": metadata,
        "source_files": sorted(
            quest_sources + item_sources, key=lambda item: item["path"]
        ),
        "records": sorted(quest_records + item_records, key=lambda item: item["id"]),
    }
    manifest_ids = [item["id"] for item in manifest["records"]]
    if len(manifest_ids) != len(set(manifest_ids)):
        duplicates = [
            logical_id
            for logical_id, count in Counter(manifest_ids).items()
            if count > 1
        ]
        raise ValueError(f"duplicate manifest IDs: {duplicates[:3]}")
    report = {
        "version": VERSION_SLUG,
        "quest_records": len(quest_records),
        **item_report,
        "catalog_entries": len(catalog["entries"]),
        "catalog_quest_entries": len(quest_catalog),
        "catalog_item_entries": len(item_catalog),
        "manifest_records": len(manifest["records"]),
        "catalog_hits": len(quest_catalog) + len(item_catalog),
        "native_ru_coverage": sum(
            item["native_ru_present"] and item["id"] not in item_catalog
            for item in item_records
        ),
        "pending": sum(
            not item["native_ru_present"] and item["id"] not in item_catalog
            for item in item_records
        ),
        "errors": 0,
        "output_quest_strings": len(quest_records),
        "output_item_translation_keys": sum(
            not item["native_ru_present"] and item["id"] in item_catalog
            for item in item_records
        ),
    }
    return json_bytes(catalog), json_bytes(manifest), json_bytes(report)


def parse_args() -> argparse.Namespace:
    translation_root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--modpack-root",
        type=Path,
        default=Path.home() / "Projects/minecraft-industrial-backrooms",
    )
    parser.add_argument(
        "--minecraft-root",
        type=Path,
        default=Path.home() / "Library/Application Support/sklauncher",
    )
    parser.add_argument(
        "--translation-root", type=Path, default=translation_root
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="verify that generated files are current without writing",
    )
    args = parser.parse_args()
    args.modpack_root = args.modpack_root.resolve()
    args.minecraft_root = args.minecraft_root.resolve()
    args.translation_root = args.translation_root.resolve()
    return args


def main() -> int:
    args = parse_args()
    try:
        catalog, manifest, report = build(args)
        outputs = {
            args.translation_root / "translation-catalog/catalog.json": catalog,
            args.translation_root
            / f"translation-versions/{VERSION_SLUG}/manifest.json": manifest,
            args.translation_root
            / f"translation-versions/{VERSION_SLUG}/migration-report.json": report,
        }
        if args.check:
            stale = [
                str(path)
                for path, content in outputs.items()
                if not path.exists() or path.read_bytes() != content
            ]
            if stale:
                raise ValueError("generated files are missing or stale:\n" + "\n".join(stale))
            print("Initial catalog, manifest, and migration report are current")
            return 0
        for path, content in outputs.items():
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
            print(f"Wrote {path}")
        summary = json.loads(report)
        print(
            f"Catalog: {summary['catalog_entries']} entries; "
            f"manifest: {summary['manifest_records']} records"
        )
        return 0
    except (OSError, ValueError, json.JSONDecodeError, zipfile.BadZipFile) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
