#!/usr/bin/env python3
"""Build namespace-separated ru_ru files from reviewed item-name TSV files."""

from __future__ import annotations

import argparse
import csv
import json
import shutil
from collections import defaultdict
from pathlib import Path


def read_mapping(path: Path) -> dict[str, str]:
    with path.open(encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle, delimiter="\t")
        fields = set(reader.fieldnames or ())
        if {"translation_key", "ru_name"} <= fields:
            key_field, value_field = "translation_key", "ru_name"
        elif {"key", "proposed_ru"} <= fields:
            key_field, value_field = "key", "proposed_ru"
        else:
            raise ValueError(f"Unsupported TSV columns in {path}: {reader.fieldnames}")

        result: dict[str, str] = {}
        for row_number, row in enumerate(reader, start=2):
            key = row[key_field].strip()
            value = row[value_field].strip()
            if not key or not value:
                raise ValueError(f"Blank key/value in {path}:{row_number}")
            if key in result and result[key] != value:
                raise ValueError(f"Conflicting duplicate key in {path}:{row_number}: {key}")
            result[key] = value
        return result


def key_namespace(key: str) -> str:
    parts = key.split(".")
    if len(parts) < 3:
        raise ValueError(f"Cannot determine namespace for translation key: {key}")
    return parts[1]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--input-dir",
        type=Path,
        default=Path("item-translation-work"),
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path(
            "translation-versions/1.19.3-7-ae2-fix/payload/resourcepack"
        ),
    )
    args = parser.parse_args()

    root = Path(__file__).resolve().parents[1]
    input_dir = args.input_dir if args.input_dir.is_absolute() else root / args.input_dir
    output = args.output if args.output.is_absolute() else root / args.output
    inputs = sorted(input_dir.glob("*_names_ru.tsv"))
    if not inputs:
        raise ValueError(f"No *_names_ru.tsv files found in {input_dir}")

    combined: dict[str, str] = {}
    for path in inputs:
        for key, value in read_mapping(path).items():
            if key in combined and combined[key] != value:
                raise ValueError(f"Conflicting translation across TSV files: {key}")
            combined[key] = value

    by_namespace: dict[str, dict[str, str]] = defaultdict(dict)
    for key, value in sorted(combined.items()):
        by_namespace[key_namespace(key)][key] = value

    assets_dir = output / "assets"
    if assets_dir.exists():
        shutil.rmtree(assets_dir)
    for namespace, values in sorted(by_namespace.items()):
        path = assets_dir / namespace / "lang/ru_ru.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(
            json.dumps(values, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

    print(f"Wrote {len(combined)} translations across {len(by_namespace)} namespaces")


if __name__ == "__main__":
    main()
