#!/usr/bin/env python3
"""Extract FTB Quests text from SNBT into compact, AI-friendly TSV files."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path


FORMAT_VERSION = 1
FIELDS = {"title", "subtitle", "description"}
TOKEN_RE = re.compile(r"[A-Za-z_][A-Za-z0-9_.+-]*")


def parse_string(source: str, start: int) -> tuple[int, str]:
    if source[start] != '"':
        raise ValueError(f"expected string at character {start}")

    escaped = False
    pos = start + 1
    while pos < len(source):
        char = source[pos]
        if escaped:
            escaped = False
        elif char == "\\":
            escaped = True
        elif char == '"':
            literal = source[start : pos + 1]
            try:
                return pos + 1, json.loads(literal)
            except json.JSONDecodeError as exc:
                raise ValueError(
                    f"unsupported SNBT string at character {start}: {exc}"
                ) from exc
        pos += 1

    raise ValueError(f"unterminated string at character {start}")


def skip_space(source: str, pos: int) -> int:
    while pos < len(source) and source[pos].isspace():
        pos += 1
    return pos


def description_strings(source: str, start: int) -> tuple[int, list[dict]]:
    if source[start] != "[":
        raise ValueError(f"expected description list at character {start}")

    records: list[dict] = []
    square_depth = 1
    curly_depth = 0
    pos = start + 1

    while pos < len(source) and square_depth:
        char = source[pos]
        if char == '"':
            end, value = parse_string(source, pos)
            if square_depth == 1 and curly_depth == 0:
                records.append({"start": pos, "end": end, "original": value})
            pos = end
            continue
        if char == "[":
            square_depth += 1
        elif char == "]":
            square_depth -= 1
        elif char == "{":
            curly_depth += 1
        elif char == "}":
            curly_depth -= 1
        pos += 1

    if square_depth:
        raise ValueError(f"unterminated description list at character {start}")
    return pos, records


def extract_records(source: str) -> list[dict]:
    records: list[dict] = []
    pos = 0

    while pos < len(source):
        if source[pos] == '"':
            pos, _ = parse_string(source, pos)
            continue

        match = TOKEN_RE.match(source, pos)
        if not match:
            pos += 1
            continue

        field = match.group()
        pos = match.end()
        if field not in FIELDS:
            continue

        value_start = skip_space(source, pos)
        if value_start >= len(source) or source[value_start] != ":":
            continue
        value_start = skip_space(source, value_start + 1)

        if field == "description":
            if value_start >= len(source) or source[value_start] != "[":
                raise ValueError(
                    f"description is not a string list at character {value_start}"
                )
            pos, found = description_strings(source, value_start)
            for record in found:
                record["field"] = field
                records.append(record)
        else:
            if value_start >= len(source) or source[value_start] != '"':
                raise ValueError(
                    f"{field} is not a string at character {value_start}"
                )
            end, value = parse_string(source, value_start)
            records.append(
                {
                    "field": field,
                    "start": value_start,
                    "end": end,
                    "original": value,
                }
            )
            pos = end

    return records


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Extract title, subtitle and description strings from FTB Quests SNBT. "
            "Each output line is ID<TAB>text; only the text after the first tab "
            "should be translated."
        )
    )
    parser.add_argument(
        "--source",
        type=Path,
        default=Path("data/config/ftbquests/quests"),
        help="quest SNBT root (default: data/config/ftbquests/quests)",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("translation-work"),
        help="output directory (default: translation-work)",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="replace an existing output directory",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    source_root = args.source.resolve()
    output_root = args.output.resolve()

    if not source_root.is_dir():
        print(f"error: quest directory not found: {source_root}", file=sys.stderr)
        return 2
    if output_root.exists() and any(output_root.iterdir()) and not args.force:
        print(
            f"error: output is not empty: {output_root}\n"
            "Use --force only when it is safe to discard the current translation.",
            file=sys.stderr,
        )
        return 2

    if output_root.exists() and args.force:
        for path in sorted(output_root.rglob("*"), reverse=True):
            if path.is_file() or path.is_symlink():
                path.unlink()
            elif path.is_dir():
                path.rmdir()
    output_root.mkdir(parents=True, exist_ok=True)

    manifest = {
        "format": FORMAT_VERSION,
        "source_root": str(source_root),
        "fields": sorted(FIELDS),
        "files": {},
    }
    total_records = 0
    total_words = 0

    for source_path in sorted(source_root.rglob("*.snbt")):
        relative = source_path.relative_to(source_root)
        source = source_path.read_text(encoding="utf-8")
        try:
            extracted = extract_records(source)
        except ValueError as exc:
            print(f"error: {relative}: {exc}", file=sys.stderr)
            return 2

        records = []
        lines = []
        for extracted_record in extracted:
            text = extracted_record["original"]
            if not text:
                continue
            if "\n" in text or "\r" in text:
                print(
                    f"error: {relative} contains a multiline SNBT string",
                    file=sys.stderr,
                )
                return 2

            record_id = str(len(records) + 1)
            record = {"id": record_id, **extracted_record}
            records.append(record)
            lines.append(f"{record_id}\t{text}")
            total_words += len(text.split())

        if not records:
            continue

        translation_relative = relative.with_suffix(".txt")
        translation_path = output_root / translation_relative
        translation_path.parent.mkdir(parents=True, exist_ok=True)
        translation_path.write_text("\n".join(lines) + "\n", encoding="utf-8")

        manifest["files"][relative.as_posix()] = {
            "sha256": hashlib.sha256(source.encode("utf-8")).hexdigest(),
            "translation_file": translation_relative.as_posix(),
            "records": records,
        }
        total_records += len(records)

    map_path = output_root / "_map.json"
    map_path.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    print(f"Extracted {total_records} strings ({total_words} words)")
    print(f"Translation files: {output_root}")
    print(f"Internal map (do not translate): {map_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
