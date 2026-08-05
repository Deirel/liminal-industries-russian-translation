#!/usr/bin/env python3
"""Validate two blind language reviews and merge them after adjudication."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
from collections import Counter
from pathlib import Path

from approve_version_translations import technical_tokens


REVIEW_FIELDS = {
    "id",
    "source_hash",
    "verdict",
    "recommendation",
    "reason",
    "checked_dimensions",
    "reviewer",
    "method",
}
ADJUDICATION_FIELDS = {
    "id",
    "source_hash",
    "verdict",
    "recommendation",
    "reason",
    "adjudicator",
    "notes",
}
CONSENSUS_FIELDS = (
    "id",
    "source_hash",
    "verdict",
    "recommendation",
    "reason",
    "reviewer_a",
    "reviewer_b",
    "adjudicator",
    "review_notes",
)
DISAGREEMENT_FIELDS = (
    "id",
    "source_hash",
    "source",
    "effective_translation",
    "plain_source",
    "plain_translation",
    "context",
    "audit_class",
    "group_key",
    "verdict_a",
    "recommendation_a",
    "reason_a",
    "reviewer_a",
    "verdict_b",
    "recommendation_b",
    "reason_b",
    "reviewer_b",
)
REQUIRED_DIMENSIONS = {
    "PROSE": {
        "PUNCT",
        "GRAMMAR",
        "SYNTAX",
        "GOVERNMENT",
        "COLLOCATION",
        "COHERENCE",
        "CALQUE",
        "WORD_ORDER",
        "NATURALNESS",
        "TERMINOLOGY",
        "REGISTER",
    },
    "SHORT_LABEL": {
        "PUNCT",
        "GRAMMAR",
        "SYNTAX",
        "COLLOCATION",
        "NATURALNESS",
        "TERMINOLOGY",
        "REGISTER",
    },
    "NAME": {"COLLOCATION", "NATURALNESS", "TERMINOLOGY", "REGISTER"},
}


def read_tsv(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle, delimiter="\t"))


def write_tsv(path: Path, fields: tuple[str, ...], rows: list[dict[str, str]]) -> None:
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
        writer.writerows(rows)


def validate_review(
    corpus: list[dict[str, str]], review: list[dict[str, str]], label: str
) -> None:
    if len(review) != len(corpus):
        raise ValueError(f"{label}: expected {len(corpus)} rows, got {len(review)}")
    for index, (source, row) in enumerate(zip(corpus, review, strict=True)):
        if not REVIEW_FIELDS <= row.keys():
            raise ValueError(f"{label}: required columns are missing")
        if (row["id"], row["source_hash"]) != (source["id"], source["source_hash"]):
            raise ValueError(f"{label}: identity mismatch at row {index + 1}")
        if row["verdict"] not in {"PASS", "CHANGE", "UNCERTAIN"}:
            raise ValueError(f"{row['id']}: invalid {label} verdict")
        if not row["checked_dimensions"] or not row["reviewer"] or not row["method"]:
            raise ValueError(f"{row['id']}: incomplete {label} provenance")
        dimensions = set(row["checked_dimensions"].split("|"))
        if dimensions != REQUIRED_DIMENSIONS[source["audit_class"]]:
            raise ValueError(f"{row['id']}: incomplete {label} language dimensions")
        if row["verdict"] == "PASS" and (row["recommendation"] or row["reason"]):
            raise ValueError(f"{row['id']}: PASS contains a recommendation")
        if row["verdict"] == "CHANGE":
            if not row["recommendation"] or not row["reason"]:
                raise ValueError(f"{row['id']}: CHANGE lacks recommendation or reason")
            recommendation_tokens = tuple(technical_tokens(row["recommendation"]))
            if recommendation_tokens not in {
                tuple(technical_tokens(source["effective_translation"])),
                tuple(technical_tokens(source["source"])),
            }:
                raise ValueError(f"{row['id']}: {label} changed technical tokens")
        if row["verdict"] == "UNCERTAIN" and not row["reason"]:
            raise ValueError(f"{row['id']}: UNCERTAIN lacks reason")


def validate_summary(
    path: Path, corpus_path: Path, review: list[dict[str, str]], label: str
) -> str:
    summary = json.loads(path.read_text(encoding="utf-8"))
    expected_hash = hashlib.sha256(corpus_path.read_bytes()).hexdigest()
    counts = Counter(row["verdict"] for row in review)
    expected_counts = {verdict: counts[verdict] for verdict in ("PASS", "CHANGE", "UNCERTAIN")}
    if summary.get("input_sha256") != expected_hash:
        raise ValueError(f"{label}: summary input hash mismatch")
    if summary.get("input_rows") != len(review) or summary.get("output_rows") != len(review):
        raise ValueError(f"{label}: summary row count mismatch")
    if summary.get("counts") != expected_counts:
        raise ValueError(f"{label}: summary verdict counts mismatch")
    if summary.get("model") != "gpt-5.6-terra" or summary.get("reasoning") != "medium":
        raise ValueError(f"{label}: wrong reviewer model or reasoning")
    if summary.get("blind") is not True or not summary.get("reviewer"):
        raise ValueError(f"{label}: blind reviewer provenance is incomplete")
    return str(summary["reviewer"])


def validate_pair_provenance(
    review_a: list[dict[str, str]],
    review_b: list[dict[str, str]],
    reviewer_a: str,
    reviewer_b: str,
) -> None:
    reviewers_a = {row["reviewer"] for row in review_a}
    reviewers_b = {row["reviewer"] for row in review_b}
    methods_a = {row["method"] for row in review_a}
    methods_b = {row["method"] for row in review_b}
    if reviewers_a != {reviewer_a} or reviewers_b != {reviewer_b}:
        raise ValueError("row reviewer does not match its blind summary")
    if reviewer_a == reviewer_b:
        raise ValueError("blind reviews must use distinct reviewers")
    if len(methods_a) != 1 or len(methods_b) != 1 or methods_a == methods_b:
        raise ValueError("blind reviews must use distinct consistent methods")
    if not all("blind" in method for method in methods_a | methods_b):
        raise ValueError("review method is not blind")


def build_consensus(
    corpus: list[dict[str, str]],
    review_a: list[dict[str, str]],
    review_b: list[dict[str, str]],
    adjudication: list[dict[str, str]] | None = None,
) -> tuple[list[dict[str, str]], list[dict[str, str]]]:
    validate_review(corpus, review_a, "review-a")
    validate_review(corpus, review_b, "review-b")
    pending: list[tuple[dict[str, str], dict[str, str], dict[str, str]]] = []
    result: list[dict[str, str]] = []
    for source, first, second in zip(corpus, review_a, review_b, strict=True):
        agree = first["verdict"] == second["verdict"] == "PASS" or (
            first["verdict"] == second["verdict"] == "CHANGE"
            and first["recommendation"] == second["recommendation"]
        )
        if not agree:
            pending.append((source, first, second))
            continue
        result.append(
            {
                "id": source["id"],
                "source_hash": source["source_hash"],
                "verdict": first["verdict"],
                "recommendation": first["recommendation"],
                "reason": first["reason"] or second["reason"],
                "reviewer_a": first["reviewer"],
                "reviewer_b": second["reviewer"],
                "adjudicator": "",
                "review_notes": "INDEPENDENT_AGREEMENT",
            }
        )

    adjudicated = {row["id"]: row for row in adjudication or []}
    if len(adjudicated) != len(adjudication or []):
        raise ValueError("duplicate adjudication ID")
    if adjudication is not None and set(adjudicated) != {item[0]["id"] for item in pending}:
        raise ValueError("adjudication must cover exactly every disagreement")
    for source, first, second in pending:
        row = adjudicated.get(source["id"])
        if row is None:
            continue
        if not ADJUDICATION_FIELDS <= row.keys():
            raise ValueError("adjudication required columns are missing")
        if row["source_hash"] != source["source_hash"] or row["verdict"] not in {"PASS", "CHANGE"}:
            raise ValueError(f"{source['id']}: invalid adjudication identity or verdict")
        if not row["adjudicator"] or not row["notes"]:
            raise ValueError(f"{source['id']}: adjudication lacks provenance")
        if row["verdict"] == "CHANGE":
            if not row["recommendation"] or not row["reason"]:
                raise ValueError(f"{source['id']}: adjudicated CHANGE is incomplete")
            recommendation_tokens = tuple(technical_tokens(row["recommendation"]))
            if recommendation_tokens not in {
                tuple(technical_tokens(source["effective_translation"])),
                tuple(technical_tokens(source["source"])),
            }:
                raise ValueError(f"{source['id']}: adjudication changed technical tokens")
        elif row["recommendation"] or row["reason"]:
            raise ValueError(f"{source['id']}: adjudicated PASS contains a recommendation")
        result.append(
            {
                "id": source["id"],
                "source_hash": source["source_hash"],
                "verdict": row["verdict"],
                "recommendation": row["recommendation"],
                "reason": row["reason"],
                "reviewer_a": first["reviewer"],
                "reviewer_b": second["reviewer"],
                "adjudicator": row["adjudicator"],
                "review_notes": row["notes"],
            }
        )
    order = {row["id"]: index for index, row in enumerate(corpus)}
    result.sort(key=lambda row: order[row["id"]])
    disagreements = [
        {
            "id": source["id"],
            "source_hash": source["source_hash"],
            "source": source["source"],
            "effective_translation": source["effective_translation"],
            "plain_source": source["plain_source"],
            "plain_translation": source["plain_translation"],
            "context": source["context"],
            "audit_class": source["audit_class"],
            "group_key": source["group_key"],
            "verdict_a": first["verdict"],
            "recommendation_a": first["recommendation"],
            "reason_a": first["reason"],
            "reviewer_a": first["reviewer"],
            "verdict_b": second["verdict"],
            "recommendation_b": second["recommendation"],
            "reason_b": second["reason"],
            "reviewer_b": second["reviewer"],
        }
        for source, first, second in pending
    ]
    return result, disagreements


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--corpus", type=Path, required=True)
    parser.add_argument("--review-a", type=Path, required=True)
    parser.add_argument("--review-b", type=Path, required=True)
    parser.add_argument("--summary-a", type=Path, required=True)
    parser.add_argument("--summary-b", type=Path, required=True)
    parser.add_argument("--adjudication", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--check", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    corpus = read_tsv(args.corpus)
    review_a = read_tsv(args.review_a)
    review_b = read_tsv(args.review_b)
    reviewer_a = validate_summary(args.summary_a, args.corpus, review_a, "review-a")
    reviewer_b = validate_summary(args.summary_b, args.corpus, review_b, "review-b")
    validate_pair_provenance(review_a, review_b, reviewer_a, reviewer_b)
    adjudication = read_tsv(args.adjudication) if args.adjudication else None
    consensus, disagreements = build_consensus(corpus, review_a, review_b, adjudication)
    consensus_path = args.output / "review-consensus.tsv"
    disagreement_path = args.output / "review-disagreements.tsv"
    summary_path = args.output / "review-consensus-summary.json"
    summary = {
        "corpus_rows": len(corpus),
        "review_a_rows": len(review_a),
        "review_b_rows": len(review_b),
        "consensus_rows": len(consensus),
        "disagreements": len(disagreements),
        "adjudicated": len(disagreements) if adjudication is not None else 0,
        "verdicts": dict(sorted(Counter(row["verdict"] for row in consensus).items())),
    }
    summary_content = json.dumps(summary, ensure_ascii=False, indent=2) + "\n"
    if args.check:
        if read_tsv(consensus_path) != consensus or read_tsv(disagreement_path) != disagreements:
            raise ValueError("merged language review is not current")
        if summary_path.read_text(encoding="utf-8") != summary_content:
            raise ValueError("merged language review summary is not current")
    else:
        write_tsv(consensus_path, CONSENSUS_FIELDS, consensus)
        write_tsv(disagreement_path, DISAGREEMENT_FIELDS, disagreements)
        summary_path.write_text(summary_content, encoding="utf-8")
    print(f"Merged {len(consensus)}/{len(corpus)} rows; {len(disagreements)} disagreements")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as exc:
        print(f"error: {exc}")
        raise SystemExit(2)
