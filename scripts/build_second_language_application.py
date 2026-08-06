#!/usr/bin/env python3
"""Build the reviewed application ledgers for the second language pass."""

from __future__ import annotations

import argparse
import csv
import json
from collections import Counter
from pathlib import Path

from approve_version_translations import technical_tokens_compatible
from merge_second_language_reviews import CONSENSUS_FIELDS, read_tsv, write_tsv


APPROVED_FIELDS = (
    "id", "source_hash", "old_translation", "new_translation", "reason",
    "independent_review", "reviewer", "initial_new_translation", "review_notes",
)
CORRECTION_FIELDS = (
    "id", "source_hash", "old_translation", "new_translation", "reason",
    "independent_review",
)
OVERRIDE_FIELDS = ("id", "source_hash", "source", "verdict", "recommendation")
VERDICT_FIELDS = ("id", "source_hash", "verdict", "reviewer", "method")


def write_rows(path: Path, fields: tuple[str, ...], rows: list[dict[str, str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(
            handle, fieldnames=fields, delimiter="\t", lineterminator="\n",
            quoting=csv.QUOTE_ALL,
        )
        writer.writeheader()
        writer.writerows(rows)


def render_rows(fields: tuple[str, ...], rows: list[dict[str, str]]) -> str:
    from io import StringIO

    output = StringIO()
    writer = csv.DictWriter(
        output, fieldnames=fields, delimiter="\t", lineterminator="\n",
        quoting=csv.QUOTE_ALL,
    )
    writer.writeheader()
    writer.writerows(rows)
    return output.getvalue()


def approved_row(
    source: dict[str, str], decision: dict[str, str], reviewer: str, notes: str
) -> dict[str, str]:
    return {
        "id": source["id"],
        "source_hash": source["source_hash"],
        "old_translation": source["effective_translation"],
        "new_translation": decision["recommendation"],
        "reason": decision["reason"],
        "independent_review": "APPROVED",
        "reviewer": reviewer,
        "initial_new_translation": decision["recommendation"],
        "review_notes": notes,
    }


def apply_review_fixes(
    version: str,
    corpus: list[dict[str, str]],
    consensus: list[dict[str, str]],
    fixes: list[dict[str, str]],
    reviewer: str,
) -> list[dict[str, str]]:
    sources = {row["id"]: row for row in corpus}
    selected = {row["id"]: row for row in fixes if row["version"] == version}
    result = [dict(row) for row in consensus]
    by_id = {row["id"]: row for row in result}
    for logical_id, fix in selected.items():
        source = sources.get(logical_id)
        decision = by_id.get(logical_id)
        if source is None or decision is None or fix["source_hash"] != source["source_hash"]:
            raise ValueError(f"{logical_id}: checkpoint review fix identity changed")
        if decision["verdict"] != "CHANGE" or not fix["recommendation"] or not fix["reason"]:
            raise ValueError(f"{logical_id}: checkpoint review fix is incomplete")
        if not technical_tokens_compatible(
            source["source"], fix["recommendation"], source["effective_translation"]
        ):
            raise ValueError(f"{logical_id}: checkpoint review fix changed technical tokens")
        decision.update({
            "recommendation": fix["recommendation"],
            "reason": fix["reason"],
            "adjudicator": reviewer,
            "review_notes": "CHECKPOINT12_REVIEW_FIX",
        })
    return result


def build(
    original_corpus: list[dict[str, str]],
    original_consensus: list[dict[str, str]],
    original_effective: list[dict[str, str]],
    rescripted_corpus: list[dict[str, str]],
    rescripted_consensus: list[dict[str, str]],
    rescripted_effective: list[dict[str, str]],
    reviewer: str,
) -> dict[str, list[dict[str, str]] | dict[str, object]]:
    oc = {row["id"]: row for row in original_corpus}
    oe = {row["id"]: row for row in original_effective}
    rc = {row["id"]: row for row in rescripted_corpus}
    re = {row["id"]: row for row in rescripted_effective}
    if any(len(rows) != len(index) for rows, index in (
        (original_corpus, oc), (original_effective, oe),
        (rescripted_corpus, rc), (rescripted_effective, re),
    )):
        raise ValueError("application inputs contain duplicate IDs")

    original_changes = [row for row in original_consensus if row["verdict"] == "CHANGE"]
    rescripted_final = [dict(row) for row in rescripted_consensus]
    rescripted_by_id = {row["id"]: row for row in rescripted_final}
    catalog_changes = [row for row in original_changes if oe[row["id"]]["origin"] == "catalog"]

    propagated: list[dict[str, str]] = []
    for decision in catalog_changes:
        logical_id = decision["id"]
        source = oc[logical_id]
        target = rc.get(logical_id)
        target_effective = re.get(logical_id)
        if target is None or target_effective is None:
            raise ValueError(f"{logical_id}: shared catalog correction is absent from Rescripted")
        if (
            (target["source_hash"], target["source"], target["effective_translation"])
            != (source["source_hash"], source["source"], source["effective_translation"])
        ):
            raise ValueError(f"{logical_id}: shared catalog correction identity differs")
        current = rescripted_by_id[logical_id]
        if current["verdict"] == "CHANGE" and current["recommendation"] != decision["recommendation"]:
            raise ValueError(f"{logical_id}: conflicting Rescripted recommendation")
        current.update({
            "verdict": "CHANGE",
            "recommendation": decision["recommendation"],
            "reason": decision["reason"],
            "adjudicator": reviewer,
            "review_notes": "SHARED_CATALOG_PROPAGATION",
        })
        propagated.append(current)

    rescripted_changes = [row for row in rescripted_final if row["verdict"] == "CHANGE"]
    for decision, source in [
        *((row, oc[row["id"]]) for row in original_changes),
        *((row, rc[row["id"]]) for row in rescripted_changes),
    ]:
        if not technical_tokens_compatible(
            source["source"], decision["recommendation"], source["effective_translation"]
        ):
            raise ValueError(f"{source['id']}: application changed technical tokens")

    original_approved = [
        approved_row(oc[row["id"]], row, reviewer, "SECOND_LANGUAGE_REVIEW")
        for row in original_changes
    ]
    propagated_ids = {row["id"] for row in propagated}
    rescripted_approved = [
        approved_row(
            rc[row["id"]], row, reviewer,
            "SHARED_CATALOG_PROPAGATION" if row["id"] in propagated_ids else "SECOND_LANGUAGE_REVIEW",
        )
        for row in rescripted_changes
    ]
    corrections = [
        {key: value for key, value in approved_row(
            oc[row["id"]], row, reviewer, "SHARED_CATALOG_CORRECTION"
        ).items() if key in CORRECTION_FIELDS}
        for row in catalog_changes
    ]

    original_catalog_ids = {row["id"] for row in catalog_changes}
    original_overrides = [row for row in original_changes if row["id"] not in original_catalog_ids]
    rescripted_overrides = [
        row for row in rescripted_changes
        if row["id"] not in original_catalog_ids or re[row["id"]]["origin"] == "translation-overrides"
    ]

    def override_rows(changes: list[dict[str, str]], corpus: dict[str, dict[str, str]]) -> list[dict[str, str]]:
        return [{
            "id": row["id"], "source_hash": row["source_hash"],
            "source": corpus[row["id"]]["source"], "verdict": "CHANGE",
            "recommendation": row["recommendation"],
        } for row in changes]

    def verdict_rows(consensus: list[dict[str, str]], method: str) -> list[dict[str, str]]:
        return [{
            "id": row["id"], "source_hash": row["source_hash"],
            "verdict": row["verdict"],
            "reviewer": "|".join(filter(None, (
                row.get("reviewer_a", ""), row.get("reviewer_b", ""), row.get("adjudicator", "")
            ))),
            "method": method,
        } for row in consensus]

    summary = {
        "reviewer": reviewer,
        "original_changes": len(original_changes),
        "rescripted_changes": len(rescripted_changes),
        "shared_catalog_corrections": len(catalog_changes),
        "propagated_to_rescripted": len(propagated),
        "original_overrides": len(original_overrides),
        "rescripted_overrides": len(rescripted_overrides),
        "rescripted_final_verdicts": dict(sorted(Counter(row["verdict"] for row in rescripted_final).items())),
    }
    expected = (71, 47, 13, 13, 58, 35)
    actual = tuple(summary[key] for key in (
        "original_changes", "rescripted_changes", "shared_catalog_corrections",
        "propagated_to_rescripted", "original_overrides", "rescripted_overrides",
    ))
    if actual != expected:
        raise ValueError(f"application counts changed: expected {expected}, got {actual}")
    return {
        "original_approved": original_approved,
        "rescripted_approved": rescripted_approved,
        "corrections": corrections,
        "original_overrides": override_rows(original_overrides, oc),
        "rescripted_overrides": override_rows(rescripted_overrides, rc),
        "original_verdicts": verdict_rows(original_consensus, "second-language-double-blind-reviewed"),
        "rescripted_verdicts": verdict_rows(rescripted_final, "second-language-double-blind-reviewed"),
        "rescripted_final": rescripted_final,
        "summary": summary,
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--versions-root", type=Path, default=Path("translation-versions"))
    parser.add_argument("--reviewer", default="/root/checkpoint12_second_review")
    parser.add_argument(
        "--review-fixes", type=Path,
        default=Path("docs/translation-quality-audit/checkpoint-12-review-fixes.tsv"),
    )
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--verify-applied", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    original = args.versions_root / "1.19.3-original"
    rescripted = args.versions_root / "1.19.3-7-ae2-fix"
    ow = original / "work/language-audit-2026-08-06"
    rw = rescripted / "work/language-audit-2026-08-06"
    original_corpus = read_tsv(ow / "corpus.tsv")
    rescripted_corpus = read_tsv(rw / "corpus.tsv")
    fixes = read_tsv(args.review_fixes)
    if len(fixes) != 12:
        raise ValueError("checkpoint 12 review fix ledger must contain 12 rows")
    artifacts = build(
        original_corpus,
        apply_review_fixes(
            "1.19.3-original", original_corpus,
            read_tsv(ow / "full-review-consensus.tsv"), fixes, args.reviewer,
        ),
        read_tsv(original / "work/effective-translations.tsv"),
        rescripted_corpus,
        apply_review_fixes(
            "1.19.3-7-ae2-fix", rescripted_corpus,
            read_tsv(rw / "review-consensus.tsv"), fixes, args.reviewer,
        ),
        read_tsv(rescripted / "work/effective-translations.tsv"), args.reviewer,
    )
    targets = (
        (ow / "approved-changes.tsv", APPROVED_FIELDS, artifacts["original_approved"]),
        (rw / "approved-changes.tsv", APPROVED_FIELDS, artifacts["rescripted_approved"]),
        (ow / "application/catalog-corrections.tsv", CORRECTION_FIELDS, artifacts["corrections"]),
        (ow / "application/overrides/override-changes.tsv", OVERRIDE_FIELDS, artifacts["original_overrides"]),
        (rw / "application/overrides/override-changes.tsv", OVERRIDE_FIELDS, artifacts["rescripted_overrides"]),
        (ow / "application/verdicts.tsv", VERDICT_FIELDS, artifacts["original_verdicts"]),
        (rw / "application/verdicts.tsv", VERDICT_FIELDS, artifacts["rescripted_verdicts"]),
        (rw / "review-consensus-final.tsv", CONSENSUS_FIELDS, artifacts["rescripted_final"]),
    )
    summary_content = json.dumps(artifacts["summary"], ensure_ascii=False, indent=2) + "\n"
    summary_path = ow / "application/application-summary.json"
    if args.check:
        for path, fields, rows in targets:
            if path.read_text(encoding="utf-8") != render_rows(fields, rows):
                raise ValueError(f"{path}: application artifact is not current")
        if summary_path.read_text(encoding="utf-8") != summary_content:
            raise ValueError("application summary is not current")
    else:
        for path, fields, rows in targets:
            write_rows(path, fields, rows)
        summary_path.parent.mkdir(parents=True, exist_ok=True)
        summary_path.write_text(summary_content, encoding="utf-8")
    if args.verify_applied:
        catalog = json.loads(Path("translation-catalog/catalog.json").read_text(encoding="utf-8"))
        history = {
            (row["id"], row["source_hash"], row["new_translation"])
            for row in catalog.get("corrections", [])
        }
        for correction in artifacts["corrections"]:
            key = (
                correction["id"], correction["source_hash"],
                correction["new_translation"],
            )
            if key not in history:
                raise ValueError(f"{correction['id']}: catalog correction is absent")
        for root, approved in (
            (original, artifacts["original_approved"]),
            (rescripted, artifacts["rescripted_approved"]),
        ):
            effective = {
                row["id"]: row
                for row in read_tsv(root / "work/effective-translations.tsv")
            }
            manifest = json.loads((root / "manifest.json").read_text(encoding="utf-8"))
            records = {row["id"]: row for row in manifest["records"]}
            for change in approved:
                logical_id = change["id"]
                if effective[logical_id]["effective_translation"] != change["new_translation"]:
                    raise ValueError(f"{logical_id}: approved text is not effective")
                if change["old_translation"] == change["new_translation"]:
                    raise ValueError(f"{logical_id}: regression ledger did not change text")
                record = records[logical_id]
                if (
                    record.get("effective_translation") != change["new_translation"]
                    or record.get("effective_translation_approved") is not True
                ):
                    raise ValueError(f"{logical_id}: applied manifest approval is stale")
    print(json.dumps(artifacts["summary"], ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as exc:
        print(f"error: {exc}")
        raise SystemExit(2)
