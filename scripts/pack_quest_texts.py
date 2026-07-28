#!/usr/bin/env python3
"""Pack translated TSV text back into FTB Quests SNBT files."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import sys
import tempfile
from datetime import datetime
from pathlib import Path


FORMAT_VERSION = 1


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Validate and write translated ID<TAB>text files back into FTB "
            "Quests SNBT. The source must still match the extraction snapshot."
        )
    )
    parser.add_argument(
        "--input",
        type=Path,
        default=Path("translation-work"),
        help="translation directory (default: translation-work)",
    )
    parser.add_argument(
        "--source",
        type=Path,
        help="override the quest SNBT root stored in _map.json",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="validate everything without writing files",
    )
    parser.add_argument(
        "--no-backup",
        action="store_true",
        help="do not back up changed SNBT files before writing",
    )
    return parser.parse_args()


def read_translations(path: Path, expected_ids: set[str]) -> dict[str, str]:
    translations: dict[str, str] = {}
    for line_number, line in enumerate(
        path.read_text(encoding="utf-8").splitlines(), start=1
    ):
        if not line:
            raise ValueError(f"{path}:{line_number}: blank line")
        if "\t" not in line:
            raise ValueError(
                f"{path}:{line_number}: expected ID followed by a tab"
            )
        record_id, text = line.split("\t", 1)
        if record_id in translations:
            raise ValueError(f"{path}:{line_number}: duplicate ID {record_id}")
        if record_id not in expected_ids:
            raise ValueError(f"{path}:{line_number}: unknown ID {record_id}")
        if "\r" in text or "\n" in text:
            raise ValueError(f"{path}:{line_number}: multiline text is not allowed")
        translations[record_id] = text

    missing = expected_ids - translations.keys()
    if missing:
        missing_list = ", ".join(sorted(missing, key=int))
        raise ValueError(f"{path}: missing IDs: {missing_list}")
    return translations


def atomic_write(path: Path, content: str) -> None:
    mode = path.stat().st_mode
    with tempfile.NamedTemporaryFile(
        mode="w",
        encoding="utf-8",
        dir=path.parent,
        prefix=f".{path.name}.",
        delete=False,
    ) as handle:
        handle.write(content)
        temp_path = Path(handle.name)
    os.chmod(temp_path, mode)
    os.replace(temp_path, path)


def main() -> int:
    args = parse_args()
    input_root = args.input.resolve()
    map_path = input_root / "_map.json"

    if not map_path.is_file():
        print(f"error: map not found: {map_path}", file=sys.stderr)
        return 2

    try:
        manifest = json.loads(map_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        print(f"error: cannot read {map_path}: {exc}", file=sys.stderr)
        return 2

    if manifest.get("format") != FORMAT_VERSION:
        print(
            f"error: unsupported map format: {manifest.get('format')}",
            file=sys.stderr,
        )
        return 2

    source_root = (
        args.source.resolve()
        if args.source
        else Path(manifest["source_root"]).resolve()
    )
    prepared: list[tuple[Path, Path, str, int]] = []
    total_strings = 0

    try:
        for relative_string, file_info in manifest["files"].items():
            relative = Path(relative_string)
            source_path = source_root / relative
            translation_path = input_root / file_info["translation_file"]

            if not source_path.is_file():
                raise ValueError(f"source file not found: {source_path}")
            if not translation_path.is_file():
                raise ValueError(f"translation file not found: {translation_path}")

            source = source_path.read_text(encoding="utf-8")
            actual_hash = hashlib.sha256(source.encode("utf-8")).hexdigest()
            if actual_hash != file_info["sha256"]:
                raise ValueError(
                    f"{source_path} changed after extraction; extract a fresh "
                    "translation set before packing"
                )

            records = file_info["records"]
            expected_ids = {record["id"] for record in records}
            translations = read_translations(translation_path, expected_ids)

            replacements = []
            changed_count = 0
            for record in records:
                translated = translations[record["id"]]
                if translated == record["original"]:
                    continue
                replacements.append(
                    (
                        record["start"],
                        record["end"],
                        json.dumps(translated, ensure_ascii=False),
                    )
                )
                changed_count += 1

            if not replacements:
                continue

            updated = source
            for start, end, replacement in sorted(replacements, reverse=True):
                updated = updated[:start] + replacement + updated[end:]

            prepared.append((source_path, relative, updated, changed_count))
            total_strings += changed_count
    except (KeyError, OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2

    print(
        f"Validated {len(manifest['files'])} files; "
        f"{total_strings} translated strings in {len(prepared)} files"
    )
    if args.check or not prepared:
        if args.check:
            print("Check only: no files written")
        return 0

    backup_root = None
    if not args.no_backup:
        timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
        backup_root = input_root / "backups" / timestamp
        for source_path, relative, _, _ in prepared:
            backup_path = backup_root / relative
            backup_path.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source_path, backup_path)

    for source_path, _, updated, _ in prepared:
        atomic_write(source_path, updated)

    print(f"Updated quest files under: {source_root}")
    if backup_root:
        print(f"Backup: {backup_root}")
    print("Re-extract before starting the next translation cycle.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
