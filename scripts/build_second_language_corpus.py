#!/usr/bin/env python3
"""Build a blind, context-grouped corpus for the second language audit."""

from __future__ import annotations

import argparse
import csv
import json
import re
from collections import Counter
from pathlib import Path


FIELDS = (
    "id",
    "source_hash",
    "source",
    "effective_translation",
    "plain_source",
    "plain_translation",
    "context",
    "audit_class",
    "group_key",
    "sequence",
)
PROSE_KINDS = {
    "quest_description",
    "mantle_book_text",
    "patchouli_language_text",
    "patchouli_json_text",
    "manual_text",
    "mantle_book_language",
    "manual_language_text",
    "patchouli_book_metadata",
}
NAME_KINDS = {"item_name", "block_name"}
CONTROL_IDS = {
    "patchouli-lang:botania:botania.page.welcome0",
    "patchouli-lang:botania:botania.page.itemFinder0",
}
PATCHOULI_TOKEN_RE = re.compile(r"\$\(([^)]*)\)")
ANGLE_TOKEN_RE = re.compile(r"<([^>]+)>")


def read_tsv(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle, delimiter="\t"))


def plain_text(value: str) -> str:
    def patchouli(match: re.Match[str]) -> str:
        token = match.group(1)
        if token == "p":
            return "\n\n"
        if token.startswith("li"):
            return "\n- "
        if token.startswith("br") or token == "n":
            return "\n"
        if token.startswith("k:"):
            return f"[keybind:{token[2:]}]"
        return ""

    def angle(match: re.Match[str]) -> str:
        token = match.group(1)
        if token == "np":
            return "\n\n"
        if token.startswith("br") or token == "n":
            return "\n"
        parts = token.split(";")
        if parts[0] == "link" and len(parts) >= 3:
            return parts[2]
        return ""

    value = PATCHOULI_TOKEN_RE.sub(patchouli, value)
    value = ANGLE_TOKEN_RE.sub(angle, value)
    value = re.sub(r"§.", "", value)
    lines = [re.sub(r"[ \t]+", " ", line).strip() for line in value.splitlines()]
    return re.sub(r"\n{3,}", "\n\n", "\n".join(lines)).strip()


def audit_class(kind: str) -> str:
    if kind in PROSE_KINDS:
        return "PROSE"
    if kind in NAME_KINDS:
        return "NAME"
    return "SHORT_LABEL"


def group_key(row: dict[str, str], context: dict[str, object]) -> str:
    kind = str(context["kind"])
    location = context.get("location", {})
    if not isinstance(location, dict):
        location = {}
    if kind in NAME_KINDS:
        translation_key = row["id"].split(":", 1)[-1]
        parts = translation_key.split(".")
        namespace = parts[1] if len(parts) > 2 else "unknown"
        return f"names:{kind}:{namespace}"
    if kind.startswith("quest_"):
        return f"quests:{location.get('file', 'unknown')}"
    member = str(location.get("output_member") or location.get("member") or "unknown")
    member = member.replace("/en_us/", "/<language>/").replace(
        "/ru_ru/", "/<language>/"
    )
    return f"{context.get('source_id', 'unknown')}:{member}"


def build_corpus(rows: list[dict[str, str]]) -> list[dict[str, str]]:
    result: list[dict[str, str]] = []
    for row in rows:
        context = json.loads(row["context"])
        if context["state"] != "visible":
            continue
        kind = str(context["kind"])
        location = context.get("location", {})
        sequence = json.dumps(location, ensure_ascii=False, sort_keys=True)
        result.append(
            {
                "id": row["id"],
                "source_hash": row["source_hash"],
                "source": row["source"],
                "effective_translation": row["effective_translation"],
                "plain_source": plain_text(row["source"]),
                "plain_translation": plain_text(row["effective_translation"]),
                "context": row["context"],
                "audit_class": audit_class(kind),
                "group_key": group_key(row, context),
                "sequence": sequence,
            }
        )
    order = {"PROSE": 0, "SHORT_LABEL": 1, "NAME": 2}
    def natural_key(value: str) -> tuple[tuple[int, object], ...]:
        return tuple(
            (0, int(part)) if part.isdigit() else (1, part)
            for part in re.split(r"(\d+)", value)
        )

    result.sort(
        key=lambda row: (
            order[row["audit_class"]],
            row["group_key"],
            natural_key(row["sequence"]),
            row["id"],
        )
    )
    return result


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


def parse_args() -> argparse.Namespace:
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", required=True)
    parser.add_argument("--versions-root", type=Path, default=root / "translation-versions")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--check", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    version_root = args.versions_root.resolve() / args.version
    rows = build_corpus(read_tsv(version_root / "work/effective-translations.tsv"))
    output = args.output or version_root / "work/language-audit-2026-08-06"
    target = output / "corpus.tsv"
    counts = Counter(row["audit_class"] for row in rows)
    found_controls = sorted(CONTROL_IDS & {row["id"] for row in rows})
    summary = {
        "version": args.version,
        "visible_records": len(rows),
        "classes": dict(sorted(counts.items())),
        "groups": len({row["group_key"] for row in rows}),
        "control_ids": found_controls,
        "control_ids_complete": found_controls == sorted(CONTROL_IDS),
        "prior_verdicts_exposed": False,
    }
    summary_target = output / "corpus-summary.json"
    summary_content = json.dumps(summary, ensure_ascii=False, indent=2) + "\n"
    if args.check:
        if read_tsv(target) != rows or summary_target.read_text(encoding="utf-8") != summary_content:
            raise ValueError("second language corpus is not current")
    else:
        write_tsv(target, rows)
        summary_target.write_text(summary_content, encoding="utf-8")
    if not summary["control_ids_complete"]:
        raise ValueError("second language corpus lacks control IDs")
    print(f"Built {len(rows)} visible rows for {args.version}: {dict(sorted(counts.items()))}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as exc:
        print(f"error: {exc}")
        raise SystemExit(2)
