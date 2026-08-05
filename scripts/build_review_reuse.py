#!/usr/bin/env python3
"""Reuse quality verdicts only for exact cross-version translation records."""

from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path


EXACT_FIELDS = ("id", "source_hash", "source", "effective_translation", "context")


def read_tsv(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle, delimiter="\t"))


def write_tsv(path: Path, rows: list[dict[str, str]], fields: tuple[str, ...]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=fields,
            delimiter="\t",
            lineterminator="\n",
            quoting=csv.QUOTE_ALL,
        )
        writer.writeheader()
        writer.writerows({field: row[field] for field in fields} for row in rows)


def exact_match(left: dict[str, str], right: dict[str, str]) -> bool:
    return all(left[field] == right[field] for field in EXACT_FIELDS)


def parse_args() -> argparse.Namespace:
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--from-version", required=True)
    parser.add_argument("--to-version", required=True)
    parser.add_argument(
        "--versions-root", type=Path, default=root / "translation-versions"
    )
    parser.add_argument("--check", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    versions = args.versions_root.resolve()
    source_root = versions / args.from_version / "work"
    target_root = versions / args.to_version / "work"
    source_quality = {row["id"]: row for row in read_tsv(source_root / "quality-review.tsv")}
    source_changes = {
        row["id"]: row
        for row in read_tsv(source_root / "quality-audit-2026-08-05/approved-changes.tsv")
    }
    target_rows = read_tsv(target_root / "effective-translations.tsv")
    reused: list[dict[str, str]] = []
    pending: list[dict[str, str]] = []
    changes: list[dict[str, str]] = []
    for row in target_rows:
        source = source_quality.get(row["id"])
        if source is not None and exact_match(row, source):
            reused.append(
                {
                    "id": row["id"],
                    "source_hash": row["source_hash"],
                    "verdict": source["verdict"],
                    "reviewer": source["reviewer"],
                    "method": f"exact-reuse-from-{args.from_version}",
                }
            )
            if source["verdict"] == "CHANGE":
                try:
                    changes.append(source_changes[row["id"]])
                except KeyError as exc:
                    raise ValueError(f"{row['id']}: reused CHANGE lacks approval") from exc
        elif json.loads(row["context"])["state"] == "visible":
            pending.append(row)

    output = target_root / "quality-audit-2026-08-05"
    artifacts = {
        output / "reused-verdicts.tsv": (
            reused,
            ("id", "source_hash", "verdict", "reviewer", "method"),
        ),
        output / "reused-changes.tsv": (
            changes,
            tuple(next(iter(source_changes.values())).keys()),
        ),
        output / "review-input.tsv": (pending, tuple(target_rows[0].keys())),
    }
    if args.check:
        for path, (rows, _) in artifacts.items():
            if read_tsv(path) != rows:
                raise ValueError(f"{path.name} is not current")
    else:
        for path, (rows, fields) in artifacts.items():
            write_tsv(path, rows, fields)
    summary = {
        "from_version": args.from_version,
        "to_version": args.to_version,
        "records": len(target_rows),
        "exact_reused": len(reused),
        "reused_changes": len(changes),
        "visible_review_required": len(pending),
        "suppressed": len(target_rows) - len(reused) - len(pending),
    }
    summary_path = output / "reuse-summary.json"
    content = json.dumps(summary, ensure_ascii=False, indent=2) + "\n"
    if args.check:
        if summary_path.read_text(encoding="utf-8") != content:
            raise ValueError("reuse summary is not current")
    else:
        summary_path.write_text(content, encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as exc:
        print(f"error: {exc}")
        raise SystemExit(2)
