#!/usr/bin/env python3
"""Export the effective Russian text for every manifest record."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
from copy import deepcopy
from pathlib import Path
from typing import Any

from build_version_resources import optional_catalog_translation
from catalog_utils import validate_catalog
from translation_sources import json_pointer_get, parse_mantle_language


FIELDS = (
    "id",
    "source_hash",
    "source",
    "effective_translation",
    "context",
    "version",
    "origin",
)


def record_context(record: dict[str, Any], state: str) -> str:
    return json.dumps(
        {
            "kind": record["kind"],
            "source_id": record["source_id"],
            "location": record.get("location", {}),
            "state": state,
        },
        ensure_ascii=False,
        sort_keys=True,
    )


def output_member(record: dict[str, Any]) -> str | None:
    if record.get("output_format") == "lang":
        return f"assets/{record['namespace']}/lang/ru_ru.json"
    return record.get("location", {}).get("output_member")


def payload_value(root: Path, record: dict[str, Any]) -> str | None:
    location = record.get("location", {})
    output_format = record.get("output_format")
    if output_format == "lang":
        path = root / f"assets/{record['namespace']}/lang/ru_ru.json"
        if not path.exists():
            return None
        value = json.loads(path.read_text(encoding="utf-8")).get(
            record["translation_key"]
        )
    elif output_format in {"patchouli_json", "mantle_book_json"}:
        path = root / location["output_member"]
        if not path.exists():
            return None
        try:
            value = json_pointer_get(
                json.loads(path.read_text(encoding="utf-8")),
                location["pointer"],
            )
        except (KeyError, IndexError, TypeError, ValueError):
            return None
    elif output_format == "manual_text":
        path = root / location["output_member"]
        if not path.exists():
            return None
        lines = path.read_text(encoding="utf-8").splitlines()
        value = lines[location["line"]] if location["line"] < len(lines) else None
    elif output_format == "mantle_book_language":
        path = root / location["output_member"]
        if not path.exists():
            return None
        value = parse_mantle_language(path.read_bytes(), str(path)).get(location["key"])
    else:
        return None
    return value if isinstance(value, str) and value else None


def effective_row(
    record: dict[str, Any],
    version: str,
    catalog: dict[str, Any],
    payload_root: Path,
    overrides_root: Path | None = None,
) -> dict[str, str]:
    catalog_value = optional_catalog_translation(catalog, record)
    value = payload_value(payload_root, record)
    if record["source_id"] == "quests":
        value = catalog_value
    override_value = (
        payload_value(overrides_root, record) if overrides_root is not None else None
    )
    if override_value is not None and value != override_value:
        raise ValueError(f"{record['id']}: final payload does not contain its override")
    suppressed = (
        value is None
        and overrides_root is not None
        and record.get("output_format") != "lang"
        and (member := output_member(record)) is not None
        and (overrides_root / member).exists()
    )
    if override_value is not None:
        origin = "translation-overrides"
    elif value is not None:
        origin = "catalog" if value == catalog_value else "translation-overrides"
    elif suppressed:
        value = ""
        origin = "translation-overrides"
    else:
        value = record.get("native_translation")
        origin = "native_ru" if value else "missing"
    return {
        "id": record["id"],
        "source_hash": record["source_hash"],
        "source": record["source"],
        "effective_translation": value or "",
        "context": record_context(
            record, "suppressed_by_override" if suppressed else "visible"
        ),
        "version": version,
        "origin": origin,
    }


def render(rows: list[dict[str, str]]) -> str:
    from io import StringIO

    output = StringIO(newline="")
    writer = csv.DictWriter(
        output,
        fieldnames=FIELDS,
        delimiter="\t",
        lineterminator="\n",
        quoting=csv.QUOTE_ALL,
    )
    writer.writeheader()
    writer.writerows(rows)
    return output.getvalue()


def runtime_sample(
    rows: list[dict[str, str]], report: dict[str, Any], size: int
) -> list[dict[str, str]]:
    by_id = {row["id"]: row for row in rows}
    sample: list[dict[str, str]] = []
    for entry in report["entries"]:
        keys = entry.get("translation_keys", [])
        if len(keys) != 1 or (logical_id := f"item:{keys[0]}") not in by_id:
            continue
        row = by_id[logical_id]
        runtime_value = entry.get("display_name", "")
        if row["effective_translation"] != runtime_value:
            raise ValueError(f"{logical_id}: export differs from runtime display_name")
        sample.append(
            {
                "id": logical_id,
                "effective_translation": row["effective_translation"],
                "runtime_translation": runtime_value,
                "report_generated_at": report["generated_at"],
            }
        )
        if len(sample) == size:
            break
    if len(sample) != size:
        raise ValueError(f"runtime report supplied only {len(sample)} comparable rows")
    return sample


def render_runtime_sample(rows: list[dict[str, str]]) -> str:
    from io import StringIO

    output = StringIO(newline="")
    writer = csv.DictWriter(
        output,
        fieldnames=(
            "id",
            "effective_translation",
            "runtime_translation",
            "report_generated_at",
        ),
        delimiter="\t",
        lineterminator="\n",
        quoting=csv.QUOTE_ALL,
    )
    writer.writeheader()
    writer.writerows(rows)
    return output.getvalue()


def annotate_manifest(
    manifest: dict[str, Any],
    rows: list[dict[str, str]],
    review_rows: list[dict[str, str]] | None = None,
    derived_changes: list[dict[str, str]] | None = None,
) -> dict[str, Any]:
    result = deepcopy(manifest)
    by_id = {row["id"]: row for row in rows}
    reviews = {row["id"]: row for row in review_rows or []}
    derived = {row["id"]: row for row in derived_changes or []}
    if len(by_id) != len(rows) or len(reviews) != len(review_rows or []):
        raise ValueError("effective or review rows contain duplicate IDs")

    for record in result["records"]:
        row = by_id[record["id"]]
        approved = False
        if review_rows is not None:
            review = reviews.get(record["id"])
            if review is None:
                raise ValueError(f"{record['id']}: missing quality-review verdict")
            if any(
                review[key] != row[key]
                for key in ("source_hash", "source", "context")
            ):
                raise ValueError(f"{record['id']}: quality-review identity changed")
            expected = (
                review["recommendation"]
                if review["verdict"] == "CHANGE"
                else review["effective_translation"]
            )
            if change := derived.get(record["id"]):
                if (
                    change["old_translation"] != expected
                    or change["new_translation"] != row["effective_translation"]
                    or change["independent_review"] != "APPROVED"
                ):
                    raise ValueError(f"{record['id']}: invalid derived change")
                expected = change["new_translation"]
            if expected != row["effective_translation"]:
                raise ValueError(f"{record['id']}: reviewed translation changed")
            if review["verdict"] == "CHANGE" and review["independent_review"] != "APPROVED":
                raise ValueError(f"{record['id']}: change lacks independent review")
            approved = True
        elif (
            record.get("effective_translation") == row["effective_translation"]
            and record.get("effective_translation_origin") == row["origin"]
            and record.get("effective_translation_context") == row["context"]
        ):
            approved = bool(record.get("effective_translation_approved", False))

        record["effective_translation"] = row["effective_translation"]
        record["effective_translation_origin"] = row["origin"]
        record["effective_translation_context"] = row["context"]
        record["effective_translation_approved"] = approved
    return result


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
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--runtime-report", type=Path)
    parser.add_argument("--sample-size", type=int, default=10)
    parser.add_argument("--quality-review", type=Path)
    parser.add_argument("--derived-changes", type=Path, action="append", default=[])
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    version_root = args.versions_root.resolve() / args.version
    manifest = json.loads((version_root / "manifest.json").read_text(encoding="utf-8"))
    catalog = json.loads(args.catalog.read_text(encoding="utf-8"))
    validate_catalog(catalog)
    rows = [
        effective_row(
            record,
            args.version,
            catalog,
            version_root / "payload/resourcepack",
            version_root / "translation-overrides",
        )
        for record in manifest["records"]
    ]
    missing = [row["id"] for row in rows if row["origin"] == "missing"]
    if missing:
        raise ValueError(f"{len(missing)} records have no effective translation: {missing[:3]}")
    target = version_root / "work/effective-translations.tsv"
    content = render(rows)
    review_rows = None
    if args.quality_review is not None:
        with args.quality_review.open(encoding="utf-8", newline="") as handle:
            review_rows = list(csv.DictReader(handle, delimiter="\t"))
    derived_changes: list[dict[str, str]] = []
    for path in args.derived_changes:
        with path.open(encoding="utf-8", newline="") as handle:
            derived_changes.extend(csv.DictReader(handle, delimiter="\t"))
    annotated_manifest = annotate_manifest(
        manifest, rows, review_rows, derived_changes
    )
    if args.check:
        if manifest != annotated_manifest:
            raise ValueError("manifest effective translations are not current")
        if not target.exists() or target.read_text(encoding="utf-8") != content:
            raise ValueError("effective translation export is not current")
        print(f"Effective translations are current for {args.version}: {len(rows)} rows")
    else:
        (version_root / "manifest.json").write_text(
            json.dumps(annotated_manifest, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8", newline="")
        print(f"Wrote {len(rows)} effective translations for {args.version}")
    if args.runtime_report is not None:
        report_bytes = args.runtime_report.read_bytes()
        report = json.loads(report_bytes)
        if report.get("result") != "PASS":
            raise ValueError("runtime report did not pass")
        sample_content = render_runtime_sample(
            runtime_sample(rows, report, args.sample_size)
        )
        sample_target = version_root / "work/runtime-sample.tsv"
        if args.check:
            if (
                not sample_target.exists()
                or sample_target.read_text(encoding="utf-8") != sample_content
            ):
                raise ValueError("runtime sample is not current")
        else:
            sample_target.write_text(sample_content, encoding="utf-8", newline="")
        print(
            f"Verified {args.sample_size} runtime rows; report SHA-256 "
            f"{hashlib.sha256(report_bytes).hexdigest()}"
        )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as exc:
        print(f"error: {exc}")
        raise SystemExit(2)
