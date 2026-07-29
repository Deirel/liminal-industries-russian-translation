#!/usr/bin/env python3
"""Validate reviewed version TSV files and append them to the immutable catalog."""

from __future__ import annotations

import argparse
import csv
import json
import re
from copy import deepcopy
from pathlib import Path
from typing import Any

from build_initial_catalog import source_hash, validate_catalog


TOKEN_RE = re.compile(
    r"\$\([^)]*\)|</?[a-z0-9_:-]+>|"
    r"&[0-9a-fklmnor]|§.|%\d*\$?[sd]|\{image:[^}]+\}|"
    r"\b[a-z0-9_.-]+:[a-z0-9_./-]+\b",
    re.IGNORECASE,
)


def json_bytes(value: Any) -> bytes:
    return (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def load_translations(paths: list[Path]) -> dict[str, dict[str, str]]:
    result: dict[str, dict[str, str]] = {}
    for path in paths:
        with path.open(encoding="utf-8", newline="") as handle:
            reader = csv.DictReader(handle, delimiter="\t")
            if not {"id", "source_hash", "translation"} <= set(
                reader.fieldnames or ()
            ):
                raise ValueError(f"{path}: expected id, source_hash, translation")
            for line, row in enumerate(reader, start=2):
                logical_id = row["id"]
                if logical_id in result:
                    raise ValueError(f"{path}:{line}: duplicate ID {logical_id}")
                if not row["translation"]:
                    raise ValueError(f"{path}:{line}: blank translation")
                result[logical_id] = {
                    "source_hash": row["source_hash"],
                    "translation": row["translation"],
                }
    return result


def catalog_translation(
    catalog: dict[str, Any], record: dict[str, Any]
) -> str | None:
    matches = [
        variant["translation"]
        for variant in catalog["entries"].get(record["id"], [])
        if variant["source_hash"] == record["source_hash"]
        and variant["source"] == record["source"]
    ]
    if len(matches) > 1:
        raise ValueError(f"{record['id']}: duplicate catalog variants")
    return matches[0] if matches else None


def validate_block_item_consistency(
    manifest_records: dict[str, dict[str, Any]],
    translations: dict[str, dict[str, str]],
    catalog: dict[str, Any],
) -> None:
    items_by_registry_id = {
        record["item_id"]: record
        for record in manifest_records.values()
        if record.get("kind") == "item_name"
    }
    for logical_id, reviewed in translations.items():
        block = manifest_records[logical_id]
        if block.get("kind") != "block_name":
            continue
        item = items_by_registry_id.get(block["block_id"])
        if item is None:
            continue
        expected = catalog_translation(catalog, item)
        if expected is None and item["id"] in translations:
            expected = translations[item["id"]]["translation"]
        if expected is not None and reviewed["translation"] != expected:
            raise ValueError(
                f"{logical_id}: block translation {reviewed['translation']!r} "
                f"must match item translation {expected!r} from {item['id']}"
            )


def parse_args() -> argparse.Namespace:
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", required=True)
    parser.add_argument(
        "--catalog", type=Path, default=root / "translation-catalog/catalog.json"
    )
    parser.add_argument(
        "--versions-root", type=Path, default=root / "translation-versions"
    )
    parser.add_argument(
        "--allow-partial",
        action="store_true",
        help="approve the reviewed subset without requiring every pending row",
    )
    parser.add_argument("--check", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    version_root = args.versions_root.resolve() / args.version
    manifest = json.loads((version_root / "manifest.json").read_text(encoding="utf-8"))
    pending_rows = list(
        csv.DictReader(
            (version_root / "work/pending.tsv").open(encoding="utf-8", newline=""),
            delimiter="\t",
        )
    )
    translation_paths = sorted(version_root.glob("work/*-translations.tsv"))
    translations = load_translations(translation_paths)
    manifest_records = {record["id"]: record for record in manifest["records"]}
    pending_ids = {row["id"] for row in pending_rows}
    missing = sorted(pending_ids - translations.keys())
    if missing and not args.allow_partial:
        raise ValueError(f"translation coverage mismatch; missing={missing[:3]}")

    for logical_id, translation in translations.items():
        record = manifest_records.get(logical_id)
        if record is None:
            raise ValueError(f"{logical_id}: not present in manifest")
        if translation["source_hash"] != record["source_hash"]:
            raise ValueError(f"{logical_id}: source hash does not match manifest")
        if source_hash(record["source"]) != record["source_hash"]:
            raise ValueError(f"{logical_id}: invalid manifest source hash")
        if TOKEN_RE.findall(record["source"]) != TOKEN_RE.findall(
            translation["translation"]
        ):
            raise ValueError(f"{logical_id}: formatting or technical tokens changed")

    catalog = json.loads(args.catalog.read_text(encoding="utf-8"))
    validate_catalog(catalog)
    validate_block_item_consistency(manifest_records, translations, catalog)
    old_entries = deepcopy(catalog["entries"])
    added = 0
    for logical_id in sorted(translations):
        record = manifest_records[logical_id]
        approved = translations[logical_id]["translation"]
        variants = catalog["entries"].setdefault(logical_id, [])
        existing = next(
            (
                variant
                for variant in variants
                if variant["source_hash"] == record["source_hash"]
            ),
            None,
        )
        if existing:
            if existing["source"] != record["source"] or existing["translation"] != approved:
                raise ValueError(f"{logical_id}: catalog entry conflicts with review")
            continue
        variants.append(
            {
                "source_hash": record["source_hash"],
                "source": record["source"],
                "translation": approved,
            }
        )
        variants.sort(key=lambda value: value["source_hash"])
        added += 1

    for logical_id, variants in old_entries.items():
        current = catalog["entries"].get(logical_id, [])
        if any(variant not in current for variant in variants):
            raise ValueError(f"{logical_id}: immutable catalog history changed")
    catalog["entries"] = dict(sorted(catalog["entries"].items()))
    validate_catalog(catalog)
    if args.check:
        if added:
            raise ValueError(f"catalog is missing {added} approved translations")
        print(f"Catalog is current for {args.version}: {len(translations)} translations")
        return 0

    args.catalog.write_bytes(json_bytes(catalog))
    print(f"Added {added} approved translations for {args.version}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as exc:
        print(f"error: {exc}")
        raise SystemExit(2)
