#!/usr/bin/env python3
"""Build quest and item resources by walking one version manifest."""

from __future__ import annotations

import argparse
import json
import shutil
import tempfile
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

from build_initial_catalog import SnbtParser, sha256_bytes, validate_catalog
from extract_quest_texts import extract_records


def json_bytes(value: Any) -> bytes:
    return (json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n").encode(
        "utf-8"
    )


def catalog_translation(catalog: dict[str, Any], record: dict[str, Any]) -> str:
    matches = [
        variant["translation"]
        for variant in catalog["entries"].get(record["id"], [])
        if variant["source_hash"] == record["source_hash"]
        and variant["source"] == record["source"]
    ]
    if len(matches) != 1:
        raise ValueError(f"{record['id']}: expected one exact catalog translation")
    return matches[0]


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


def build_quests(
    manifest: dict[str, Any],
    catalog: dict[str, Any],
    instance_root: Path,
    output: Path,
) -> int:
    source_root = instance_root / "config/ftbquests/quests"
    quest_records = [
        record for record in manifest["records"] if record["kind"] != "item_name"
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


def build_items(
    manifest: dict[str, Any], catalog: dict[str, Any], output: Path
) -> int:
    assets = output / "assets"
    if assets.exists():
        shutil.rmtree(assets)
    by_namespace: dict[str, dict[str, str]] = defaultdict(dict)
    for record in manifest["records"]:
        if record["kind"] != "item_name" or record["native_ru_present"]:
            continue
        key = record["translation_key"]
        value = catalog_translation(catalog, record)
        previous = by_namespace[record["namespace"]].get(key)
        if previous is not None and previous != value:
            raise ValueError(f"{key}: conflicting output translations")
        by_namespace[record["namespace"]][key] = value

    for namespace, values in sorted(by_namespace.items()):
        target = assets / namespace / "lang/ru_ru.json"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(json_bytes(values))
    return sum(len(values) for values in by_namespace.values())


def snapshot(root: Path) -> dict[str, bytes]:
    return {
        path.relative_to(root).as_posix(): path.read_bytes()
        for path in sorted(root.rglob("*"))
        if path.is_file()
    }


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
    parser.add_argument("--output", type=Path, default=root / "src")
    parser.add_argument("--check", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    version_root = args.versions_root.resolve() / args.version
    manifest = json.loads((version_root / "manifest.json").read_text(encoding="utf-8"))
    if manifest["version"] != args.version:
        raise ValueError("manifest version does not match requested version")
    catalog = json.loads(args.catalog.read_text(encoding="utf-8"))
    validate_catalog(catalog)

    destination = args.output.resolve()
    with tempfile.TemporaryDirectory(prefix="liminal-translation-") as temp:
        generated = Path(temp) / "src"
        resourcepack = generated / "resourcepack"
        resourcepack.mkdir(parents=True)
        pack_meta = destination / "resourcepack/pack.mcmeta"
        shutil.copy2(pack_meta, resourcepack / "pack.mcmeta")
        quest_count = build_quests(
            manifest, catalog, args.instance_root.resolve(), generated / "quests"
        )
        item_count = build_items(manifest, catalog, resourcepack)
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
            record["kind"] != "item_name" or not record["native_ru_present"]
            for record in manifest["records"]
        ),
        "native_ru_coverage": sum(
            record["kind"] == "item_name" and record["native_ru_present"]
            for record in manifest["records"]
        ),
        "pending": 0,
        "errors": 0,
        "output_quest_strings": quest_count,
        "output_item_translation_keys": item_count,
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
            f"Built {quest_count} quest strings and {item_count} item keys "
            f"for {args.version}"
        )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as exc:
        print(f"error: {exc}")
        raise SystemExit(2)
