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

from build_initial_catalog import (
    parse_snbt,
    require_string,
    sha256_bytes,
    source_hash,
    validate_catalog,
)
from extract_item_names import (
    candidate_keys,
    load_item_model_ids,
    load_jar_languages,
    load_minecraft_languages,
    load_probe_item_ids,
)


SCHEMA_VERSION = 1
FTB_QUESTS_RE = re.compile(r"ftb-quests-forge-([0-9.]+)\.jar$")


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


def extract_item_records(
    instance_root: Path, launcher_root: Path
) -> tuple[list[dict[str, Any]], list[dict[str, str]], dict[str, Any]]:
    mods_root = instance_root / "mods"
    kubejs_root = instance_root / "kubejs"
    en_us, native_ru = load_jar_languages(mods_root)
    load_minecraft_languages(launcher_root, en_us, native_ru)
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
    if not registry_ids:
        archives = sorted(mods_root.glob("*.jar"))
        minecraft_jar = launcher_root / "versions/1.20.1/1.20.1.jar"
        if minecraft_jar.exists():
            archives.append(minecraft_jar)
        registry_ids = load_item_model_ids(archives, kubejs_root)
        registry_source = "item_models"
    if not registry_ids:
        raise ValueError("could not build an item registry")

    records: list[dict[str, Any]] = []
    by_key: dict[str, str] = {}
    for item_id in sorted(registry_ids):
        key = next(
            (candidate for candidate in candidate_keys(item_id) if candidate in en_us),
            candidate_keys(item_id)[0],
        )
        previous = by_key.get(key)
        if previous and previous != item_id:
            raise ValueError(f"translation key {key} maps to {previous} and {item_id}")
        by_key[key] = item_id
        source = en_us.get(key) or fallback_name(item_id)
        records.append(
            {
                "id": f"item:{key}",
                "kind": "item_name",
                "source": source,
                "source_hash": source_hash(source),
                "namespace": item_id.split(":", 1)[0],
                "item_id": item_id,
                "translation_key": key,
                "native_ru_present": key in native_ru,
                "source_origin": (
                    "effective_en_us" if key in en_us else "runtime_generated"
                ),
            }
        )

    source_paths = sorted(mods_root.glob("*.jar"))
    source_paths += sorted(kubejs_root.glob("assets/*/lang/*.json"))
    source_paths += sorted(kubejs_root.glob("contentpacks/*/assets/*/lang/*.json"))
    source_paths += resource_files
    minecraft_jar = launcher_root / "versions/1.20.1/1.20.1.jar"
    if minecraft_jar.exists():
        source_paths.append(minecraft_jar)
    source_files = [
        {
            "path": (
                str(path.relative_to(instance_root))
                if path.is_relative_to(instance_root)
                else str(path)
            ),
            "sha256": sha256_bytes(path.read_bytes()),
        }
        for path in sorted(set(source_paths))
    ]
    report = {
        "registry_source": registry_source,
        "registry_items": len(registry_ids),
        "native_ru_items": sum(record["native_ru_present"] for record in records),
        "generated_english_names": sum(
            record["source_origin"] == "runtime_generated" for record in records
        ),
    }
    return records, source_files, report


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
        raise ValueError(f"instance metadata not found for {instance_root}")
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
        "minecraft_version": instance["minecraftVersion"],
        "forge_version": instance["loaderVersion"],
        "ftb_quests_version": ftb_versions[0],
    }


def catalog_has(catalog: dict[str, Any], record: dict[str, Any]) -> bool:
    return any(
        variant["source_hash"] == record["source_hash"]
        and variant["source"] == record["source"]
        for variant in catalog["entries"].get(record["id"], [])
    )


def build(args: argparse.Namespace) -> tuple[dict[str, Any], dict[str, Any], list[dict[str, Any]]]:
    catalog = json.loads(args.catalog.read_text(encoding="utf-8"))
    validate_catalog(catalog)
    metadata = detect_metadata(
        args.instance_root, args.launcher_root, args.sklauncher_manifest
    )
    quest_records, quest_sources = extract_quest_records(
        args.instance_root / "config/ftbquests/quests"
    )
    item_records, item_sources, item_report = extract_item_records(
        args.instance_root, args.launcher_root
    )
    records = sorted(quest_records + item_records, key=lambda item: item["id"])
    duplicate_ids = [
        logical_id
        for logical_id, count in Counter(record["id"] for record in records).items()
        if count > 1
    ]
    if duplicate_ids:
        raise ValueError(f"duplicate manifest IDs: {duplicate_ids[:3]}")

    pending: list[dict[str, Any]] = []
    categories: Counter[str] = Counter()
    for record in records:
        if record["kind"] == "item_name" and record["native_ru_present"]:
            categories["NATIVE_RU"] += 1
        elif catalog_has(catalog, record):
            categories["CATALOG_HIT"] += 1
        else:
            categories["PENDING"] += 1
            pending.append(record)

    manifest = {
        "schema_version": SCHEMA_VERSION,
        "version": args.version_slug,
        "modpack": metadata,
        "source_files": sorted(
            quest_sources + item_sources, key=lambda item: item["path"]
        ),
        "records": records,
    }
    report = {
        "version": args.version_slug,
        "modpack": metadata,
        "quest_records": len(quest_records),
        "item_records": len(item_records),
        **item_report,
        "catalog_hits": categories["CATALOG_HIT"],
        "native_ru_coverage": categories["NATIVE_RU"],
        "pending": categories["PENDING"],
        "pending_quests": sum(
            record["kind"] != "item_name" for record in pending
        ),
        "pending_items": sum(
            record["kind"] == "item_name" for record in pending
        ),
        "errors": 0,
    }
    return manifest, report, pending


def write_pending(path: Path, pending: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle, delimiter="\t", lineterminator="\n")
        writer.writerow(("id", "kind", "source_hash", "source", "translation"))
        for record in pending:
            writer.writerow(
                (
                    record["id"],
                    record["kind"],
                    record["source_hash"],
                    record["source"],
                    "",
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
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    args.instance_root = args.instance_root.resolve()
    args.launcher_root = args.launcher_root.resolve()
    args.sklauncher_manifest = args.sklauncher_manifest.resolve()
    args.catalog = args.catalog.resolve()
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
