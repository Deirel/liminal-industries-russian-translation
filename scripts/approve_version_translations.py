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

from catalog_utils import source_hash, validate_catalog
from catalog_utils import catalog_entries_hash


TOKEN_RE = re.compile(
    r"\$\([^)]*\)|<[^>]+>|"
    r"&[0-9a-fklmnor]|§.|%\d*\$?[sd]|\{image:[^}]+\}|"
    r"\b[a-z0-9_.-]+:[a-z0-9_./-]+\b",
    re.IGNORECASE,
)
CORRECTION_FIELDS = (
    "id",
    "source_hash",
    "old_translation",
    "new_translation",
    "reason",
    "independent_review",
)


def json_bytes(value: Any) -> bytes:
    return (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def technical_tokens(value: str) -> list[str]:
    result: list[str] = []
    for token in TOKEN_RE.findall(value):
        if token.startswith("<link;"):
            parts = token[1:-1].split(";")
            if len(parts) < 3:
                result.append("<invalid-link>")
            else:
                result.append(
                    ";".join([parts[0], parts[1], "*", *parts[3:]])
                )
        else:
            result.append(token)
    return result


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
    parser.add_argument(
        "--corrections",
        type=Path,
        help="apply an independently approved correction TSV",
    )
    return parser.parse_args()


def apply_corrections(
    catalog: dict[str, Any],
    manifest_records: dict[str, dict[str, Any]],
    rows: list[dict[str, str]],
) -> int:
    required = set(CORRECTION_FIELDS)
    changed = 0
    seen: set[tuple[str, str]] = set()
    corrections = catalog.setdefault("corrections", [])
    for row in rows:
        if not required <= row.keys():
            raise ValueError("correction TSV lacks required columns")
        logical_id = row["id"]
        digest = row["source_hash"]
        pair = (logical_id, digest)
        if pair in seen:
            raise ValueError(f"{logical_id}: duplicate correction")
        seen.add(pair)
        record = manifest_records.get(logical_id)
        if record is None or record["source_hash"] != digest:
            raise ValueError(f"{logical_id}: correction does not match manifest")
        if source_hash(record["source"]) != digest:
            raise ValueError(f"{logical_id}: invalid manifest source hash")
        if not row["reason"].strip() or row["independent_review"] != "APPROVED":
            raise ValueError(f"{logical_id}: correction lacks independent approval")
        variants = catalog["entries"].get(logical_id, [])
        variant = next((value for value in variants if value["source_hash"] == digest), None)
        if variant is None:
            raise ValueError(f"{logical_id}: correction requires an existing approval")
        if variant["translation"] != row["old_translation"]:
            raise ValueError(f"{logical_id}: old translation does not match catalog")
        new_translation = row["new_translation"]
        if not new_translation or new_translation == row["old_translation"]:
            raise ValueError(f"{logical_id}: correction must change the translation")
        if technical_tokens(record["source"]) != technical_tokens(new_translation):
            raise ValueError(f"{logical_id}: formatting or technical tokens changed")
        variant["translation"] = new_translation
        corrections.append({key: row[key] for key in CORRECTION_FIELDS})
        changed += 1
    catalog["entries_hash"] = catalog_entries_hash(catalog["entries"])
    return changed


def add_translations(
    catalog: dict[str, Any],
    manifest_records: dict[str, dict[str, Any]],
    translations: dict[str, dict[str, str]],
) -> int:
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
    catalog["entries"] = dict(sorted(catalog["entries"].items()))
    catalog["entries_hash"] = catalog_entries_hash(catalog["entries"])
    return added


def main() -> int:
    args = parse_args()
    version_root = args.versions_root.resolve() / args.version
    manifest = json.loads((version_root / "manifest.json").read_text(encoding="utf-8"))
    manifest_records = {record["id"]: record for record in manifest["records"]}
    catalog = json.loads(args.catalog.read_text(encoding="utf-8"))
    validate_catalog(catalog)
    if args.corrections is not None:
        with args.corrections.open(encoding="utf-8", newline="") as handle:
            rows = list(csv.DictReader(handle, delimiter="\t"))
        changed = apply_corrections(catalog, manifest_records, rows)
        validate_catalog(catalog)
        if args.check:
            if changed:
                raise ValueError(f"catalog needs {changed} approved corrections")
        else:
            args.catalog.write_bytes(json_bytes(catalog))
        print(f"Applied {changed} approved corrections for {args.version}")
        return 0
    pending_rows = list(
        csv.DictReader(
            (version_root / "work/pending.tsv").open(encoding="utf-8", newline=""),
            delimiter="\t",
        )
    )
    translation_paths = [
        path
        for path in sorted(version_root.glob("work/*-translations.tsv"))
        if path.name != "effective-translations.tsv"
    ]
    translations = load_translations(translation_paths)
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
        if technical_tokens(record["source"]) != technical_tokens(
            translation["translation"]
        ):
            raise ValueError(f"{logical_id}: formatting or technical tokens changed")

    validate_block_item_consistency(manifest_records, translations, catalog)
    old_entries = deepcopy(catalog["entries"])
    added = add_translations(catalog, manifest_records, translations)

    for logical_id, variants in old_entries.items():
        current = catalog["entries"].get(logical_id, [])
        if any(variant not in current for variant in variants):
            raise ValueError(f"{logical_id}: immutable catalog history changed")
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
