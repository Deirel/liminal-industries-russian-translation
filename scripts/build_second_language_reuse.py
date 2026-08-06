#!/usr/bin/env python3
"""Reuse only exact, independently agreed second-pass language verdicts."""

from __future__ import annotations

import argparse
import csv
import json
from collections import Counter
from pathlib import Path

from build_second_language_corpus import FIELDS as CORPUS_FIELDS
from approve_version_translations import technical_tokens
from merge_second_language_reviews import CONSENSUS_FIELDS, read_tsv, write_tsv


def identity(row: dict[str, str]) -> tuple[str, ...]:
    return (
        row["id"],
        row["source_hash"],
        row["source"],
        row["effective_translation"],
        row["context"],
    )


def build_reuse(
    source_corpus: list[dict[str, str]],
    source_a: list[dict[str, str]],
    source_b: list[dict[str, str]],
    source_consensus: list[dict[str, str]],
    target_corpus: list[dict[str, str]],
) -> tuple[list[dict[str, str]], list[dict[str, str]]]:
    if not (
        len(source_corpus)
        == len(source_a)
        == len(source_b)
        == len(source_consensus)
    ):
        raise ValueError("source review artifacts have different row counts")
    reusable: dict[tuple[str, ...], dict[str, str]] = {}
    for corpus, first, second, consensus in zip(
        source_corpus, source_a, source_b, source_consensus, strict=True
    ):
        agreed = first["verdict"] == second["verdict"] == "PASS" or (
            first["verdict"] == second["verdict"] == "CHANGE"
            and first["recommendation"] == second["recommendation"]
        )
        if not agreed or consensus["adjudicator"]:
            continue
        if consensus["verdict"] != first["verdict"]:
            raise ValueError(f"{corpus['id']}: source consensus disagrees with reviewers")
        reusable[identity(corpus)] = consensus

    reused: list[dict[str, str]] = []
    pending: list[dict[str, str]] = []
    for row in target_corpus:
        consensus = reusable.get(identity(row))
        if consensus is None:
            pending.append(row)
        else:
            reused.append(consensus)
    if len(reused) + len(pending) != len(target_corpus):
        raise ValueError("reuse partition lost target rows")
    return reused, pending


def combine_consensus(
    target_corpus: list[dict[str, str]],
    reused: list[dict[str, str]],
    reviewed: list[dict[str, str]],
) -> list[dict[str, str]]:
    decisions = {row["id"]: row for row in [*reused, *reviewed]}
    if len(decisions) != len(reused) + len(reviewed):
        raise ValueError("reused and reviewed consensus overlap")
    if set(decisions) != {row["id"] for row in target_corpus}:
        raise ValueError("full consensus does not cover the target corpus")
    result = []
    for source in target_corpus:
        decision = decisions[source["id"]]
        if decision["source_hash"] != source["source_hash"]:
            raise ValueError(f"{source['id']}: full consensus hash mismatch")
        result.append(decision)
    return result


def apply_review_fix(
    corpus: list[dict[str, str]],
    consensus: list[dict[str, str]],
    findings: list[dict[str, str]],
    proposals: list[dict[str, str]],
) -> list[dict[str, str]]:
    by_id = {row["id"]: row for row in corpus}
    fixes = {row["id"]: row for row in proposals}
    if len(by_id) != len(corpus) or len(fixes) != len(proposals):
        raise ValueError("review fix inputs contain duplicate IDs")
    if {row["id"] for row in findings} != set(fixes):
        raise ValueError("review findings and fix proposals differ")
    required = {
        "id", "source_hash", "verdict", "recommendation", "reason", "reviewer", "method"
    }
    for logical_id, row in fixes.items():
        source = by_id.get(logical_id)
        if source is None or not required <= row.keys():
            raise ValueError(f"{logical_id}: invalid review fix proposal")
        if row["source_hash"] != source["source_hash"] or row["verdict"] != "CHANGE":
            raise ValueError(f"{logical_id}: review fix identity or verdict changed")
        if not row["recommendation"] or not row["reason"] or not row["reviewer"] or not row["method"]:
            raise ValueError(f"{logical_id}: incomplete review fix proposal")
        candidate = technical_tokens(row["recommendation"])
        accepted = {
            tuple(technical_tokens(source["source"])),
            tuple(technical_tokens(source["effective_translation"])),
        }
        if tuple(candidate) not in accepted and not (
            candidate[-1:] == ["$(0)"] and tuple(candidate[:-1]) in accepted
        ):
            raise ValueError(f"{logical_id}: review fix changed technical tokens")

    result = []
    for row in consensus:
        fix = fixes.get(row["id"])
        if fix is None:
            result.append(row)
            continue
        result.append(
            {
                **row,
                "verdict": "CHANGE",
                "recommendation": fix["recommendation"],
                "reason": fix["reason"],
                "adjudicator": fix["reviewer"],
                "review_notes": "CHECKPOINT_REVIEW_FIX",
            }
        )
    if len(result) != len(corpus) or {row["id"] for row in result} != set(by_id):
        raise ValueError("fixed consensus does not cover the review corpus")
    return result


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--target", type=Path, required=True)
    parser.add_argument("--reviewed-consensus", type=Path)
    parser.add_argument("--review-findings", type=Path)
    parser.add_argument("--fix-proposals", type=Path)
    parser.add_argument("--check", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    default_findings = args.target / "checkpoint-review-findings.tsv"
    default_proposals = args.target / "review-fix-proposals.tsv"
    if args.review_findings is None and args.fix_proposals is None:
        if default_findings.exists() and default_proposals.exists():
            args.review_findings = default_findings
            args.fix_proposals = default_proposals
    reused, pending = build_reuse(
        read_tsv(args.source / "corpus.tsv"),
        read_tsv(args.source / "review-a.tsv"),
        read_tsv(args.source / "review-b.tsv"),
        read_tsv(args.source / "review-consensus.tsv"),
        read_tsv(args.target / "corpus.tsv"),
    )
    reused_path = args.target / "reused-consensus.tsv"
    pending_path = args.target / "review-input.tsv"
    summary_path = args.target / "review-reuse-summary.json"
    summary = {
        "target_records": len(reused) + len(pending),
        "reused": len(reused),
        "pending": len(pending),
        "reused_verdicts": dict(sorted(Counter(row["verdict"] for row in reused).items())),
        "pending_classes": dict(sorted(Counter(row["audit_class"] for row in pending).items())),
        "exact_identity": [
            "id",
            "source_hash",
            "source",
            "effective_translation",
            "context",
        ],
        "requires_two_agreed_source_verdicts": True,
    }
    summary_content = json.dumps(summary, ensure_ascii=False, indent=2) + "\n"
    if args.check:
        if read_tsv(reused_path) != reused or read_tsv(pending_path) != pending:
            raise ValueError("second language reuse artifacts are not current")
        if summary_path.read_text(encoding="utf-8") != summary_content:
            raise ValueError("second language reuse summary is not current")
    else:
        write_tsv(reused_path, CONSENSUS_FIELDS, reused)
        write_tsv(pending_path, CORPUS_FIELDS, pending)
        summary_path.write_text(summary_content, encoding="utf-8")
    if bool(args.review_findings) != bool(args.fix_proposals):
        raise ValueError("review findings and fix proposals must be provided together")
    if args.reviewed_consensus:
        full = combine_consensus(
            read_tsv(args.target / "corpus.tsv"),
            reused,
            read_tsv(args.reviewed_consensus),
        )
        if args.fix_proposals:
            full = apply_review_fix(
                read_tsv(args.target / "corpus.tsv"),
                full,
                read_tsv(args.review_findings),
                read_tsv(args.fix_proposals),
            )
        full_path = args.target / "full-review-consensus.tsv"
        full_summary_path = args.target / "full-review-consensus-summary.json"
        full_summary = {
            "records": len(full),
            "reused": len(reused),
            "reviewed_original": len(full) - len(reused),
            "review_fixes": len(read_tsv(args.fix_proposals)) if args.fix_proposals else 0,
            "verdicts": dict(sorted(Counter(row["verdict"] for row in full).items())),
            "uncertain": sum(row["verdict"] == "UNCERTAIN" for row in full),
        }
        full_summary_content = json.dumps(full_summary, ensure_ascii=False, indent=2) + "\n"
        if args.check:
            if read_tsv(full_path) != full:
                raise ValueError("full Original language consensus is not current")
            if full_summary_path.read_text(encoding="utf-8") != full_summary_content:
                raise ValueError("full Original language consensus summary is not current")
        else:
            write_tsv(full_path, CONSENSUS_FIELDS, full)
            full_summary_path.write_text(full_summary_content, encoding="utf-8")
    print(f"Reused {len(reused)} exact verdicts; {len(pending)} require Original review")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as exc:
        print(f"error: {exc}")
        raise SystemExit(2)
