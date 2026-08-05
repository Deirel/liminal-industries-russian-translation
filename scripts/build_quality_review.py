#!/usr/bin/env python3
"""Build a complete PASS/CHANGE ledger from an effective translation export."""

from __future__ import annotations

import argparse
import csv
import json
from collections import Counter
from pathlib import Path

from approve_version_translations import technical_tokens


FIELDS = (
    "id",
    "source_hash",
    "source",
    "effective_translation",
    "context",
    "verdict",
    "recommendation",
    "reason",
    "independent_review",
    "reviewer",
    "method",
    "independent_reviewer",
    "review_notes",
)
CHANGE_FIELDS = {
    "id",
    "source_hash",
    "old_translation",
    "new_translation",
    "reason",
    "independent_review",
    "reviewer",
    "initial_new_translation",
    "review_notes",
}


def read_tsv(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle, delimiter="\t"))


def write_tsv(path: Path, rows: list[dict[str, str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=FIELDS,
            delimiter="\t",
            lineterminator="\n",
            quoting=csv.QUOTE_ALL,
        )
        writer.writeheader()
        writer.writerows(rows)


def build_review(
    effective_rows: list[dict[str, str]],
    change_rows: list[dict[str, str]],
    verdict_rows: list[dict[str, str]],
) -> list[dict[str, str]]:
    effective = {row["id"]: row for row in effective_rows}
    if len(effective) != len(effective_rows):
        raise ValueError("effective export contains duplicate IDs")
    changes: dict[str, dict[str, str]] = {}
    for row in change_rows:
        if not CHANGE_FIELDS <= row.keys():
            raise ValueError("approved changes lack required columns")
        logical_id = row["id"]
        current = effective.get(logical_id)
        if current is None:
            raise ValueError(f"{logical_id}: change is absent from effective export")
        if logical_id in changes:
            raise ValueError(f"{logical_id}: duplicate change")
        if row["source_hash"] != current["source_hash"]:
            raise ValueError(f"{logical_id}: source hash changed")
        if row["old_translation"] != current["effective_translation"]:
            raise ValueError(f"{logical_id}: old translation changed")
        if not row["new_translation"] or row["new_translation"] == row["old_translation"]:
            raise ValueError(f"{logical_id}: CHANGE requires a new translation")
        if not row["reason"].strip() or row["independent_review"] != "APPROVED":
            raise ValueError(f"{logical_id}: CHANGE lacks independent review")
        if not row["reviewer"] or not row["initial_new_translation"] or not row["review_notes"]:
            raise ValueError(f"{logical_id}: CHANGE lacks review provenance")
        if technical_tokens(current["source"]) != technical_tokens(row["new_translation"]):
            raise ValueError(f"{logical_id}: formatting or technical tokens changed")
        changes[logical_id] = row

    verdicts: dict[str, dict[str, str]] = {}
    required_verdict = {"id", "source_hash", "verdict", "reviewer", "method"}
    for row in verdict_rows:
        if not required_verdict <= row.keys():
            raise ValueError("verdict ledger lacks required columns")
        logical_id = row["id"]
        current = effective.get(logical_id)
        if current is None or row["source_hash"] != current["source_hash"]:
            raise ValueError(f"{logical_id}: verdict does not match effective export")
        if logical_id in verdicts:
            raise ValueError(f"{logical_id}: duplicate verdict")
        if row["verdict"] not in {"PASS", "CHANGE"}:
            raise ValueError(f"{logical_id}: invalid verdict")
        if not row["reviewer"] or not row["method"]:
            raise ValueError(f"{logical_id}: verdict lacks review provenance")
        if (row["verdict"] == "CHANGE") != (logical_id in changes):
            raise ValueError(f"{logical_id}: verdict and approved change disagree")
        verdicts[logical_id] = row

    result: list[dict[str, str]] = []
    for row in effective_rows:
        context = json.loads(row["context"])
        visible = context["state"] == "visible"
        if visible and not row["effective_translation"]:
            raise ValueError(f"{row['id']}: visible translation is empty")
        change = changes.get(row["id"])
        verdict = verdicts.get(row["id"])
        if visible and verdict is None:
            raise ValueError(f"{row['id']}: visible record lacks an individual verdict")
        result.append(
            {
                "id": row["id"],
                "source_hash": row["source_hash"],
                "source": row["source"],
                "effective_translation": row["effective_translation"],
                "context": row["context"],
                "verdict": verdict["verdict"] if verdict else "PASS",
                "recommendation": change["new_translation"] if change else "",
                "reason": (
                    change["reason"]
                    if change
                    else "Структурно заменено версионным override."
                    if not visible
                    else ""
                ),
                "independent_review": change["independent_review"] if change else "",
                "reviewer": verdict["reviewer"] if verdict else "structural-override",
                "method": verdict["method"] if verdict else "not-visible",
                "independent_reviewer": change["reviewer"] if change else "",
                "review_notes": change["review_notes"] if change else "",
            }
        )
    return result


def parse_args() -> argparse.Namespace:
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", required=True)
    parser.add_argument("--changes", type=Path, required=True)
    parser.add_argument("--verdicts", type=Path, action="append", required=True)
    parser.add_argument(
        "--versions-root", type=Path, default=root / "translation-versions"
    )
    parser.add_argument("--check", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    version_root = args.versions_root.resolve() / args.version
    rows = build_review(
        read_tsv(version_root / "work/effective-translations.tsv"),
        read_tsv(args.changes),
        [row for path in args.verdicts for row in read_tsv(path)],
    )
    target = version_root / "work/quality-review.tsv"
    source_counts = Counter()
    state_counts = Counter()
    verdict_counts = Counter(row["verdict"] for row in rows)
    for row in rows:
        context = json.loads(row["context"])
        source_counts[context["source_id"]] += 1
        state_counts[context["state"]] += 1
    summary = {
        "version": args.version,
        "records": len(rows),
        "sources": dict(sorted(source_counts.items())),
        "states": dict(sorted(state_counts.items())),
        "verdicts": dict(sorted(verdict_counts.items())),
        "unreviewed_changes": sum(
            row["verdict"] == "CHANGE" and row["independent_review"] != "APPROVED"
            for row in rows
        ),
    }
    summary_target = version_root / "work/quality-review-summary.json"
    summary_content = json.dumps(summary, ensure_ascii=False, indent=2) + "\n"
    if args.check:
        with target.open(encoding="utf-8", newline="") as handle:
            current = list(csv.DictReader(handle, delimiter="\t"))
        if current != rows:
            raise ValueError("quality review ledger is not current")
        if (
            not summary_target.exists()
            or summary_target.read_text(encoding="utf-8") != summary_content
        ):
            raise ValueError("quality review summary is not current")
    else:
        write_tsv(target, rows)
        summary_target.write_text(summary_content, encoding="utf-8")
    changes = sum(row["verdict"] == "CHANGE" for row in rows)
    print(f"Quality review complete for {args.version}: {len(rows)} rows, {changes} changes")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as exc:
        print(f"error: {exc}")
        raise SystemExit(2)
