#!/usr/bin/env python3
"""Build a deterministic manifest and translation delta for a modpack instance."""

from __future__ import annotations

import argparse
import csv
import json
import re
import zipfile
from collections import Counter
from pathlib import Path
from typing import Any

from catalog_utils import (
    parse_snbt,
    require_string,
    sha256_bytes,
    source_hash,
    validate_catalog,
)
from item_sources import (
    candidate_keys,
    load_blockstate_ids,
    load_item_model_ids,
    load_jar_languages,
    load_minecraft_languages,
    load_probe_block_ids,
    load_probe_item_ids,
    same_source_aliases,
)
from translation_sources import (
    SourceDefinition,
    SourceResult,
    collect_immersive_engineering_manual,
    collect_mantle_books,
    collect_patchouli,
    collect_sources,
    load_source_definitions,
)


SCHEMA_VERSION = 2
PROJECT_JAR_PREFIX = "liminal-industries-russian-translation-"
FTB_QUESTS_RE = re.compile(r"ftb-quests-forge-([0-9.]+)\.jar$")
KUBEJS_REGISTRY_RE = re.compile(
    r"StartupEvents\.registry\(\s*(['\"])(block|item|fluid)\1"
)
KUBEJS_CREATE_RE = re.compile(
    r"event\.create\(\s*(['\"])([a-z0-9_./-]+)\1[^)]*\)"
    r"\s*\.displayName\(\s*(['\"])(.*?)\3\s*\)",
    re.DOTALL,
)
KUBEJS_HELPER_RE = re.compile(
    r"(?:let|const|var)\s+([a-zA-Z_$][\w$]*)\s*=\s*"
    r"\(\s*id(?:\s*,\s*name)?\s*\)\s*=>\s*\{(?P<body>.*?)^\s*\}",
    re.DOTALL | re.MULTILINE,
)
KUBEJS_HELPER_CALL_RE = re.compile(
    r"^\s*([a-zA-Z_$][\w$]*)\(\s*(['\"])([a-z0-9_./-]+)\2"
    r"(?:\s*,\s*(['\"])(.*?)\4)?\s*\)",
    re.MULTILINE,
)
QUEST_ITEM_RE = re.compile(
    r"\b(?:id|item):\s*\"([a-z0-9_.-]+:[a-z0-9_./-]+)\""
)


def json_bytes(value: Any) -> bytes:
    return (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def add_record(
    records: list[dict[str, Any]],
    seen: set[str],
    logical_id: str,
    kind: str,
    source: Any,
    location: dict[str, Any],
) -> None:
    if source is None:
        return
    if not isinstance(source, str):
        raise ValueError(f"{logical_id}: source must be a string")
    if not source:
        return
    if logical_id in seen:
        raise ValueError(f"duplicate logical ID: {logical_id}")
    seen.add(logical_id)
    records.append(
        {
            "id": logical_id,
            "kind": kind,
            "source": source,
            "source_hash": source_hash(source),
            "location": location,
        }
    )


def indexed(values: Any, context: str) -> list[dict[str, Any]]:
    if not isinstance(values, list):
        raise ValueError(f"{context}: expected a list")
    result: list[dict[str, Any]] = []
    seen: set[str] = set()
    for index, value in enumerate(values):
        if not isinstance(value, dict):
            raise ValueError(f"{context}[{index}]: expected a compound")
        value_id = require_string(value, "id", f"{context}[{index}]")
        if value_id in seen:
            raise ValueError(f"{context}: duplicate ID {value_id}")
        seen.add(value_id)
        result.append(value)
    return result


def extract_quest_records(
    quests_root: Path,
) -> tuple[list[dict[str, Any]], list[dict[str, str]]]:
    records: list[dict[str, Any]] = []
    source_files: list[dict[str, str]] = []
    seen: set[str] = set()
    files = sorted(quests_root.rglob("*.snbt"))
    if not files:
        raise ValueError(f"no quest SNBT files found under {quests_root}")

    for path in files:
        relative = path.relative_to(quests_root).as_posix()
        source_files.append(
            {
                "path": f"config/ftbquests/quests/{relative}",
                "sha256": sha256_bytes(path.read_bytes()),
            }
        )
        data = parse_snbt(path)
        base = {"file": relative}
        if relative == "data.snbt":
            continue
        if relative == "chapter_groups.snbt":
            for group in indexed(data.get("chapter_groups"), relative):
                group_id = require_string(group, "id", relative)
                add_record(
                    records,
                    seen,
                    f"quest-group:{group_id}:title",
                    "quest_group_title",
                    group.get("title"),
                    {**base, "group_id": group_id, "field": "title"},
                )
            continue

        chapter_id = require_string(data, "id", relative)
        add_record(
            records,
            seen,
            f"quest-chapter:{chapter_id}:title",
            "quest_chapter_title",
            data.get("title"),
            {**base, "chapter_id": chapter_id, "field": "title"},
        )
        for quest in indexed(data.get("quests"), f"{relative}: quests"):
            quest_id = require_string(quest, "id", relative)
            quest_location = {**base, "quest_id": quest_id}
            for field in ("title", "subtitle"):
                add_record(
                    records,
                    seen,
                    f"quest:{quest_id}:{field}",
                    f"quest_{field}",
                    quest.get(field),
                    {**quest_location, "field": field},
                )

            description = quest.get("description")
            if description is not None:
                if not isinstance(description, list):
                    raise ValueError(f"quest {quest_id}: description must be a list")
                for index, source in enumerate(description):
                    add_record(
                        records,
                        seen,
                        f"quest:{quest_id}:description:{index}",
                        "quest_description",
                        source,
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
                for child in indexed(quest.get(container, []), f"quest {quest_id} {container}"):
                    child_id = require_string(child, "id", f"quest {quest_id}")
                    add_record(
                        records,
                        seen,
                        f"{prefix}:{quest_id}:{child_id}:title",
                        kind,
                        child.get("title"),
                        {
                            **quest_location,
                            "container": container,
                            "entry_id": child_id,
                            "field": "title",
                        },
                    )

    return sorted(records, key=lambda item: item["id"]), source_files


def read_loose_language(path: Path) -> dict[str, str]:
    source = path.read_text(encoding="utf-8-sig")
    try:
        parsed = json.loads(source)
    except json.JSONDecodeError:
        # Some KubeJS packs store a language-object fragment without braces.
        parsed = json.loads("{" + source.rstrip().rstrip(",") + "}")
    if not isinstance(parsed, dict):
        raise ValueError(f"{path}: language file must contain an object")
    return {str(key): str(value) for key, value in parsed.items()}


def overlay_language_paths(
    paths: list[Path], target: dict[str, str]
) -> list[Path]:
    for path in paths:
        parsed = read_loose_language(path)
        if not isinstance(parsed, dict):
            raise ValueError(f"{path}: language file must contain an object")
        target.update(parsed)
    return paths


def fallback_name(item_id: str) -> str:
    return item_id.split(":", 1)[1].replace("_", " ").replace("/", " ").title()


def kubejs_startup_scripts(kubejs_root: Path) -> list[Path]:
    return sorted(
        {
            *kubejs_root.glob("startup_scripts/**/*.js"),
            *kubejs_root.glob("contentpacks/*/startup_scripts/**/*.js"),
        }
    )


def kubejs_display_name(kind: str, display_name: str) -> str:
    return f"{display_name} Bucket" if kind == "fluid" else display_name


def load_kubejs_registry(kubejs_root: Path) -> dict[str, tuple[str, str]]:
    """Extract deterministic KubeJS registry names from the pack's startup scripts."""
    result: dict[str, tuple[str, str]] = {}
    for path in kubejs_startup_scripts(kubejs_root):
        source = path.read_text(encoding="utf-8-sig")
        sections = list(KUBEJS_REGISTRY_RE.finditer(source))
        for index, section in enumerate(sections):
            kind = section.group(2)
            key_prefix = "block" if kind == "block" else "item"
            id_suffix = "_bucket" if kind == "fluid" else ""
            end = sections[index + 1].start() if index + 1 < len(sections) else len(source)
            body = source[section.end() : end]
            for match in KUBEJS_CREATE_RE.finditer(body):
                item_path = f"{match.group(2)}{id_suffix}"
                item_id = f"kubejs:{item_path}"
                result[item_id] = (
                    f"{key_prefix}.kubejs.{item_path}",
                    kubejs_display_name(kind, match.group(4)),
                )

            helpers: dict[str, str | None] = {}
            for helper in KUBEJS_HELPER_RE.finditer(body):
                create = re.search(
                    r"event\.create\(\s*id\s*\)\s*"
                    r"\.displayName\(\s*(name|(['\"])(.*?)\2)\s*\)",
                    helper.group("body"),
                    re.DOTALL,
                )
                if create:
                    helpers[helper.group(1)] = (
                        None if create.group(1) == "name" else create.group(3)
                    )
            for call in KUBEJS_HELPER_CALL_RE.finditer(body):
                if call.group(1) not in helpers:
                    continue
                display_name = call.group(5) or helpers[call.group(1)]
                if display_name is None:
                    raise ValueError(
                        f"{path}: helper {call.group(1)} call has no display name"
                    )
                item_path = f"{call.group(3)}{id_suffix}"
                item_id = f"kubejs:{item_path}"
                result[item_id] = (
                    f"{key_prefix}.kubejs.{item_path}",
                    kubejs_display_name(kind, display_name),
                )
    return result


def load_quest_item_ids(quests_root: Path) -> set[str]:
    item_ids: set[str] = set()
    for path in sorted(quests_root.rglob("*.snbt")):
        item_ids.update(QUEST_ITEM_RE.findall(path.read_text(encoding="utf-8")))
    return item_ids


def load_item_hints(path: Path | None) -> dict[str, tuple[str, str]]:
    if path is None:
        return {}
    result: dict[str, tuple[str, str]] = {}
    with path.open(encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle, delimiter="\t")
        if not {"item_id", "translation_key", "source"} <= set(
            reader.fieldnames or ()
        ):
            raise ValueError(
                f"{path}: expected item_id, translation_key, source columns"
            )
        for line, row in enumerate(reader, start=2):
            item_id = row["item_id"]
            value = (row["translation_key"], row["source"])
            if not item_id or not all(value):
                raise ValueError(f"{path}:{line}: blank item hint")
            if item_id in result and result[item_id] != value:
                raise ValueError(f"{path}:{line}: conflicting hint for {item_id}")
            result[item_id] = value
    return result


def source_mod_archives(mods_root: Path) -> list[Path]:
    return [
        path
        for path in sorted(mods_root.glob("*.jar"))
        if not path.name.startswith(PROJECT_JAR_PREFIX)
    ]


def extract_item_records(
    instance_root: Path,
    launcher_root: Path,
    minecraft_version: str,
    quest_item_ids: set[str],
    item_hints: dict[str, tuple[str, str]],
    item_hints_path: Path | None,
) -> tuple[list[dict[str, Any]], list[dict[str, str]], dict[str, Any]]:
    mods_root = instance_root / "mods"
    kubejs_root = instance_root / "kubejs"
    archives = source_mod_archives(mods_root)
    en_us, native_ru = load_jar_languages(mods_root, archives)
    load_minecraft_languages(
        launcher_root, en_us, native_ru, minecraft_version
    )
    kubejs_en = sorted(kubejs_root.glob("assets/*/lang/en_us.json"))
    kubejs_en += sorted(
        kubejs_root.glob("contentpacks/*/assets/*/lang/en_us.json")
    )
    kubejs_ru = sorted(kubejs_root.glob("assets/*/lang/ru_ru.json"))
    kubejs_ru += sorted(
        kubejs_root.glob("contentpacks/*/assets/*/lang/ru_ru.json")
    )
    overlay_language_paths(kubejs_en, en_us)
    overlay_language_paths(kubejs_ru, native_ru)
    resource_en = sorted(
        (instance_root / "resourcepacks").glob("*/assets/*/lang/en_us.json")
    )
    resource_ru = sorted(
        (instance_root / "resourcepacks").glob("*/assets/*/lang/ru_ru.json")
    )
    resource_files = overlay_language_paths(resource_en, en_us)
    resource_files += overlay_language_paths(resource_ru, native_ru)

    registry_ids = load_probe_item_ids(kubejs_root)
    registry_source = "probejs"
    kubejs_registry = load_kubejs_registry(kubejs_root)
    if not registry_ids:
        model_archives = list(archives)
        minecraft_jar = (
            launcher_root
            / "versions"
            / minecraft_version
            / f"{minecraft_version}.jar"
        )
        if minecraft_jar.exists():
            model_archives.append(minecraft_jar)
        model_ids = load_item_model_ids(model_archives, kubejs_root)
        registry_ids = {
            item_id
            for item_id in model_ids
            if any(
                key in en_us or key in native_ru
                for key in candidate_keys(item_id)
            )
        }
        registry_ids.update(
            item_id
            for item_id, (translation_key, _) in kubejs_registry.items()
            if translation_key.startswith("item.")
        )
        registry_ids.update(quest_item_ids)
        registry_source = "language_models+quest_refs+kubejs_startup"
        if item_hints:
            registry_source += "+reviewed_hints"
    if not registry_ids:
        raise ValueError("could not build an item registry")

    records: list[dict[str, Any]] = []
    by_key: dict[str, str] = {}
    for item_id in sorted(registry_ids):
        kubejs_entry = kubejs_registry.get(item_id)
        item_hint = item_hints.get(item_id)
        key = (
            item_hint[0]
            if item_hint
            else kubejs_entry[0]
            if kubejs_entry
            else next(
                (
                    candidate
                    for candidate in candidate_keys(item_id)
                    if candidate in en_us or candidate in native_ru
                ),
                candidate_keys(item_id)[0],
            )
        )
        previous = by_key.get(key)
        if previous and previous != item_id:
            raise ValueError(f"translation key {key} maps to {previous} and {item_id}")
        by_key[key] = item_id
        source = en_us.get(key) or (
            kubejs_entry[1]
            if kubejs_entry
            else item_hint[1]
            if item_hint
            else fallback_name(item_id)
        )
        record = {
            "id": f"item:{key}",
            "kind": "item_name",
            "source": source,
            "source_hash": source_hash(source),
            "namespace": item_id.split(":", 1)[0],
            "item_id": item_id,
            "translation_key": key,
            "native_ru_present": key in native_ru,
            "output_format": "lang",
            "source_origin": (
                "effective_en_us"
                if key in en_us
                else "kubejs_startup"
                if kubejs_entry
                else "reviewed_hint"
                if item_hint
                else "runtime_generated"
            ),
        }
        if key in native_ru and native_ru[key] == source:
            record["native_ru_same_as_source"] = True
        aliases = (
            []
            if key in native_ru
            else same_source_aliases(item_id, key, source, en_us)
        )
        if aliases:
            record["translation_aliases"] = aliases
        records.append(record)

    source_paths = list(archives)
    source_paths += kubejs_startup_scripts(kubejs_root)
    source_paths += sorted(kubejs_root.glob("assets/*/lang/*.json"))
    source_paths += sorted(kubejs_root.glob("contentpacks/*/assets/*/lang/*.json"))
    source_paths += resource_files
    minecraft_jar = (
        launcher_root
        / "versions"
        / minecraft_version
        / f"{minecraft_version}.jar"
    )
    if minecraft_jar.exists():
        source_paths.append(minecraft_jar)
    if item_hints_path is not None:
        source_paths.append(item_hints_path)
    source_files = []
    for path in sorted(set(source_paths)):
        if path.is_relative_to(instance_root):
            label = path.relative_to(instance_root).as_posix()
        elif path.is_relative_to(launcher_root):
            label = f"launcher/{path.relative_to(launcher_root).as_posix()}"
        elif item_hints_path is not None and path == item_hints_path:
            label = f"translation-inputs/{path.name}"
        else:
            label = path.name
        source_files.append(
            {
                "path": label,
                "sha256": sha256_bytes(path.read_bytes()),
            }
        )
    report = {
        "registry_source": registry_source,
        "registry_items": len(registry_ids),
        "native_ru_items": sum(record["native_ru_present"] for record in records),
        "generated_english_names": sum(
            record["source_origin"] == "runtime_generated" for record in records
        ),
        "reviewed_hint_names": sum(
            record["source_origin"] == "reviewed_hint" for record in records
        ),
        "translation_aliases": sum(
            len(record.get("translation_aliases", [])) for record in records
        ),
        "kubejs_startup_names": sum(
            record["source_origin"] == "kubejs_startup" for record in records
        ),
    }
    return records, source_files, report


def load_effective_languages(
    instance_root: Path,
    launcher_root: Path,
    minecraft_version: str,
) -> tuple[list[Path], dict[str, str], dict[str, str], list[Path]]:
    mods_root = instance_root / "mods"
    kubejs_root = instance_root / "kubejs"
    archives = source_mod_archives(mods_root)
    en_us, native_ru = load_jar_languages(mods_root, archives)
    load_minecraft_languages(
        launcher_root, en_us, native_ru, minecraft_version
    )
    kubejs_en = sorted(kubejs_root.glob("assets/*/lang/en_us.json"))
    kubejs_en += sorted(
        kubejs_root.glob("contentpacks/*/assets/*/lang/en_us.json")
    )
    kubejs_ru = sorted(kubejs_root.glob("assets/*/lang/ru_ru.json"))
    kubejs_ru += sorted(
        kubejs_root.glob("contentpacks/*/assets/*/lang/ru_ru.json")
    )
    language_files = overlay_language_paths(kubejs_en, en_us)
    language_files += overlay_language_paths(kubejs_ru, native_ru)
    resource_en = sorted(
        (instance_root / "resourcepacks").glob("*/assets/*/lang/en_us.json")
    )
    resource_ru = sorted(
        (instance_root / "resourcepacks").glob("*/assets/*/lang/ru_ru.json")
    )
    language_files += overlay_language_paths(resource_en, en_us)
    language_files += overlay_language_paths(resource_ru, native_ru)
    return archives, en_us, native_ru, language_files


def load_payload_translations(version_root: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for path in sorted(
        (version_root / "payload/resourcepack").glob("assets/*/lang/ru_ru.json")
    ):
        document = json.loads(path.read_text(encoding="utf-8"))
        values.update(
            {
                str(key): str(value)
                for key, value in document.items()
                if isinstance(value, str)
            }
        )
    return values


def extract_block_records(
    instance_root: Path,
    launcher_root: Path,
    minecraft_version: str,
    version_root: Path,
    catalog: dict[str, Any],
) -> tuple[list[dict[str, Any]], list[dict[str, str]], dict[str, Any]]:
    archives, en_us, native_ru, language_files = load_effective_languages(
        instance_root, launcher_root, minecraft_version
    )
    kubejs_root = instance_root / "kubejs"
    kubejs_registry = load_kubejs_registry(kubejs_root)
    block_ids = load_probe_block_ids(kubejs_root)
    if not block_ids:
        model_archives = list(archives)
        minecraft_jar = (
            launcher_root
            / "versions"
            / minecraft_version
            / f"{minecraft_version}.jar"
        )
        if minecraft_jar.exists():
            model_archives.append(minecraft_jar)
        block_ids = load_blockstate_ids(model_archives, kubejs_root)
        block_ids.update(
            registry_id
            for registry_id, (translation_key, _) in kubejs_registry.items()
            if translation_key.startswith("block.")
        )
    if not block_ids:
        raise ValueError("could not build a block registry")
    project_ru = load_payload_translations(version_root)
    records: list[dict[str, Any]] = []
    for block_id in sorted(block_ids):
        namespace, path = block_id.split(":", 1)
        key = f"block.{namespace}.{path}"
        kubejs_entry = kubejs_registry.get(block_id)
        source = en_us.get(key) or (
            kubejs_entry[1]
            if kubejs_entry and kubejs_entry[0] == key
            else fallback_name(block_id)
        )
        record = {
            "id": f"block:{key}",
            "kind": "block_name",
            "source": source,
            "source_hash": source_hash(source),
            "namespace": namespace,
            "block_id": block_id,
            "translation_key": key,
            "native_ru_present": key in native_ru,
            "source_origin": (
                "effective_en_us"
                if key in en_us
                else "kubejs_startup"
                if kubejs_entry and kubejs_entry[0] == key
                else "runtime_generated"
            ),
            "output_format": "lang",
        }
        if key in native_ru and native_ru[key] == source:
            record["native_ru_same_as_source"] = True
        suggested = project_ru.get(key)
        item_key = f"item.{namespace}.{path}"
        if (
            suggested is None
            and en_us.get(item_key) == source
        ):
            suggested = project_ru.get(item_key) or native_ru.get(item_key)
            if suggested is None:
                matches = {
                    variant["translation"]
                    for variant in catalog["entries"].get(
                        f"item:{item_key}", []
                    )
                    if variant["source"] == source
                    and variant["source_hash"] == source_hash(source)
                }
                if len(matches) == 1:
                    suggested = next(iter(matches))
        if suggested is not None:
            record["suggested_translation"] = suggested
        records.append(record)

    source_paths = [
        *archives,
        *language_files,
        *kubejs_startup_scripts(kubejs_root),
    ]
    minecraft_jar = (
        launcher_root
        / "versions"
        / minecraft_version
        / f"{minecraft_version}.jar"
    )
    if minecraft_jar.exists():
        source_paths.append(minecraft_jar)
    source_files = []
    for path in sorted(set(source_paths)):
        if path.is_relative_to(instance_root):
            label = path.relative_to(instance_root).as_posix()
        elif path.is_relative_to(launcher_root):
            label = f"launcher/{path.relative_to(launcher_root).as_posix()}"
        else:
            label = path.name
        source_files.append({"path": label, "sha256": sha256_bytes(path.read_bytes())})
    return records, source_files, {
        "registry_blocks": len(block_ids),
        "native_ru": sum(record["native_ru_present"] for record in records),
        "suggested_translations": sum(
            "suggested_translation" in record for record in records
        ),
    }


def detect_metadata(
    instance_root: Path, launcher_root: Path, sklauncher_manifest: Path
) -> dict[str, Any]:
    launcher_manifest = json.loads(sklauncher_manifest.read_text(encoding="utf-8"))
    link = launcher_manifest["modpackLink"]
    instances = json.loads((launcher_root / "instances.json").read_text(encoding="utf-8"))
    instance = next(
        (
            value
            for value in instances.get("instances", [])
            if Path(value.get("directory", "")).resolve() == instance_root
        ),
        None,
    )
    if instance is None:
        forge_manifest_path = instance_root / ".forge-manifest.json"
        if not forge_manifest_path.exists():
            raise ValueError(f"instance metadata not found for {instance_root}")
        forge_manifest = json.loads(
            forge_manifest_path.read_text(encoding="utf-8")
        )
        minecraft_version = forge_manifest["minecraftVersion"]
        forge_version = forge_manifest["forgeVersion"]
    else:
        minecraft_version = instance["minecraftVersion"]
        forge_version = instance["loaderVersion"]
    ftb_versions = [
        match.group(1)
        for path in (instance_root / "mods").glob("*.jar")
        if (match := FTB_QUESTS_RE.search(path.name))
    ]
    if len(ftb_versions) != 1:
        raise ValueError(f"expected one FTB Quests JAR, found {ftb_versions}")
    return {
        "name": link["name"],
        "version": link["versionNumber"],
        "curseforge_project_id": int(link["projectId"]),
        "curseforge_file_id": int(link["versionId"]),
        "minecraft_version": minecraft_version,
        "forge_version": forge_version,
        "ftb_quests_version": ftb_versions[0],
    }


def catalog_has(catalog: dict[str, Any], record: dict[str, Any]) -> bool:
    return any(
        variant["source_hash"] == record["source_hash"]
        and variant["source"] == record["source"]
        for variant in catalog["entries"].get(record["id"], [])
    )


def classify_translation_record(
    catalog: dict[str, Any], record: dict[str, Any]
) -> str:
    if catalog_has(catalog, record):
        return "FINALIZED"
    if record.get("review_native", False):
        return record.get(
            "native_translation_status",
            "REVIEW_NATIVE"
            if record.get("native_ru_present", False)
            else "MISSING_NATIVE",
        )
    if record.get("native_ru_present", False):
        return "NATIVE_RU"
    return "PENDING"


def build(args: argparse.Namespace) -> tuple[dict[str, Any], dict[str, Any], list[dict[str, Any]]]:
    catalog = json.loads(args.catalog.read_text(encoding="utf-8"))
    validate_catalog(catalog)
    metadata = detect_metadata(
        args.instance_root, args.launcher_root, args.sklauncher_manifest
    )
    version_root = args.output_root.resolve() / args.version_slug
    definitions = load_source_definitions(version_root / "sources.json")

    def quests_collector(definition: SourceDefinition) -> SourceResult:
        del definition
        records, source_files = extract_quest_records(
            args.instance_root / "config/ftbquests/quests"
        )
        return SourceResult(
            records,
            source_files,
            {"quest_records": len(records)},
        )

    def items_collector(definition: SourceDefinition) -> SourceResult:
        del definition
        records, source_files, report = extract_item_records(
            args.instance_root,
            args.launcher_root,
            metadata["minecraft_version"],
            load_quest_item_ids(args.instance_root / "config/ftbquests/quests"),
            load_item_hints(args.item_hints),
            args.item_hints,
        )
        return SourceResult(records, source_files, report)

    def blocks_collector(definition: SourceDefinition) -> SourceResult:
        del definition
        records, source_files, report = extract_block_records(
            args.instance_root,
            args.launcher_root,
            metadata["minecraft_version"],
            version_root,
            catalog,
        )
        return SourceResult(records, source_files, report)

    def patchouli_collector(definition: SourceDefinition) -> SourceResult:
        archives, en_us, native_ru, _ = load_effective_languages(
            args.instance_root,
            args.launcher_root,
            metadata["minecraft_version"],
        )
        return collect_patchouli(
            definition,
            archives,
            args.instance_root,
            en_us,
            native_ru,
            load_payload_translations(version_root),
        )

    def ie_manual_collector(definition: SourceDefinition) -> SourceResult:
        archives, _, _, _ = load_effective_languages(
            args.instance_root,
            args.launcher_root,
            metadata["minecraft_version"],
        )
        return collect_immersive_engineering_manual(
            definition,
            archives,
            args.instance_root,
        )

    def mantle_books_collector(definition: SourceDefinition) -> SourceResult:
        archives, _, _, _ = load_effective_languages(
            args.instance_root,
            args.launcher_root,
            metadata["minecraft_version"],
        )
        return collect_mantle_books(
            definition,
            archives,
            args.instance_root,
        )

    collected = collect_sources(
        definitions,
        {
            "ftb_quests": quests_collector,
            "registry_items": items_collector,
            "registry_blocks": blocks_collector,
            "patchouli": patchouli_collector,
            "immersive_engineering_manual": ie_manual_collector,
            "mantle_books": mantle_books_collector,
        },
    )
    records = collected.records

    pending: list[dict[str, Any]] = []
    categories: Counter[str] = Counter()
    for record in records:
        status = classify_translation_record(catalog, record)
        record["translation_status"] = status
        categories[status] += 1
        if status not in {"FINALIZED", "NATIVE_RU"}:
            pending.append(record)

    manifest = {
        "schema_version": SCHEMA_VERSION,
        "version": args.version_slug,
        "modpack": metadata,
        "source_config": "sources.json",
        "source_config_hash": sha256_bytes(
            (version_root / "sources.json").read_bytes()
        ),
        "source_files": collected.source_files,
        "records": records,
    }
    report = {
        "version": args.version_slug,
        "modpack": metadata,
        **collected.report,
        "catalog_hits": categories["FINALIZED"],
        "finalized": categories["FINALIZED"],
        "native_ru_coverage": sum(
            record.get("native_ru_present", False) for record in records
        ),
        "pending": len(pending),
        "translation_statuses": dict(sorted(categories.items())),
        "pending_by_source": dict(
            sorted(Counter(record["source_id"] for record in pending).items())
        ),
        "errors": 0,
    }
    return manifest, report, pending


def write_pending(path: Path, pending: list[dict[str, Any]]) -> None:
    def order_key(record: dict[str, Any]):
        location = record.get("location", {})
        pointer = location.get("pointer", "")
        pointer_key = tuple(
            (0, int(token)) if token.isdigit() else (1, token)
            for token in pointer.removeprefix("/").split("/")
        )
        return (
            record.get("source_id", ""),
            location.get("output_member")
            or location.get("file")
            or record["id"],
            location.get("line", -1),
            pointer_key,
            record["id"],
        )

    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(
            handle,
            delimiter="\t",
            lineterminator="\n",
            quoting=csv.QUOTE_ALL,
        )
        writer.writerow(
            (
                "id",
                "kind",
                "status",
                "source_hash",
                "native_translation",
                "translation",
                "source",
            )
        )
        for record in sorted(pending, key=order_key):
            writer.writerow(
                (
                    record["id"],
                    record["kind"],
                    record["translation_status"],
                    record["source_hash"],
                    record.get("native_translation", ""),
                    record.get("suggested_translation", ""),
                    record["source"],
                )
            )


def parse_args() -> argparse.Namespace:
    root = Path(__file__).resolve().parents[1]
    launcher = Path.home() / "Library/Application Support/sklauncher"
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--instance-root", type=Path, required=True)
    parser.add_argument("--sklauncher-manifest", type=Path, required=True)
    parser.add_argument("--version-slug", required=True)
    parser.add_argument("--launcher-root", type=Path, default=launcher)
    parser.add_argument(
        "--catalog", type=Path, default=root / "translation-catalog/catalog.json"
    )
    parser.add_argument(
        "--output-root", type=Path, default=root / "translation-versions"
    )
    parser.add_argument("--item-hints", type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    args.instance_root = args.instance_root.resolve()
    args.launcher_root = args.launcher_root.resolve()
    args.sklauncher_manifest = args.sklauncher_manifest.resolve()
    args.catalog = args.catalog.resolve()
    if args.item_hints is not None:
        args.item_hints = args.item_hints.resolve()
    output = args.output_root.resolve() / args.version_slug
    try:
        manifest, report, pending = build(args)
        output.mkdir(parents=True, exist_ok=True)
        (output / "manifest.json").write_bytes(json_bytes(manifest))
        (output / "migration-report.json").write_bytes(json_bytes(report))
        write_pending(output / "work/pending.tsv", pending)
        print(
            f"Wrote {len(manifest['records'])} records; "
            f"{report['catalog_hits']} catalog hits, "
            f"{report['native_ru_coverage']} native Russian, "
            f"{report['pending']} pending"
        )
        return 0
    except (OSError, ValueError, KeyError, json.JSONDecodeError, zipfile.BadZipFile) as exc:
        print(f"error: {exc}")
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
