#!/usr/bin/env python3
"""Apply one-pass native Russian book reviews to translation overrides."""

from __future__ import annotations

import argparse
import csv
import io
import json
import zipfile
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from catalog_utils import source_hash
from translation_sources import json_pointer_set


VERDICT_HEADERS = ("verdict", "verdict(PASS|CHANGE|LENGTH_EXCEPTION)")
VERDICTS = {"PASS", "CHANGE", "LENGTH_EXCEPTION"}
SUPPORTED_FORMATS = {"lang", "patchouli_json"}


@dataclass(frozen=True)
class ApplyCounts:
    applied: int
    skipped: int
    files: int


def json_bytes(value: Any) -> bytes:
    serialized = json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True)
    return (serialized + "\n").encode("utf-8")


def _review_rows(review_dir: Path) -> list[dict[str, str]]:
    paths = sorted(review_dir.glob("*.tsv"))
    if not paths:
        raise ValueError(f"{review_dir}: no TSV review files")

    rows: list[dict[str, str]] = []
    seen: set[str] = set()
    for path in paths:
        with path.open(encoding="utf-8-sig", newline="") as handle:
            reader = csv.DictReader(handle, delimiter="\t")
            fields = set(reader.fieldnames or ())
            verdict_headers = [name for name in VERDICT_HEADERS if name in fields]
            if not {"id", "source_hash", "source", "recommendation"} <= fields:
                raise ValueError(
                    f"{path}: expected id, source_hash, source, and recommendation"
                )
            if len(verdict_headers) != 1:
                raise ValueError(
                    f"{path}: expected exactly one of {VERDICT_HEADERS}"
                )
            verdict_header = verdict_headers[0]
            for line, raw in enumerate(reader, start=2):
                logical_id = (raw.get("id") or "").strip()
                if not logical_id:
                    raise ValueError(f"{path}:{line}: blank ID")
                if logical_id in seen:
                    raise ValueError(f"{path}:{line}: duplicate ID {logical_id}")
                seen.add(logical_id)
                rows.append(
                    {
                        "id": logical_id,
                        "source_hash": raw.get("source_hash") or "",
                        "source": raw.get("source") or "",
                        "verdict": (raw.get(verdict_header) or "").strip(),
                        "recommendation": raw.get("recommendation") or "",
                        "origin": f"{path}:{line}",
                    }
                )
    return rows


def _validated_changes(
    manifest: dict[str, Any], rows: list[dict[str, str]]
) -> tuple[list[tuple[dict[str, Any], str]], int]:
    records: dict[str, dict[str, Any]] = {}
    for record in manifest.get("records", []):
        logical_id = record.get("id")
        if not isinstance(logical_id, str) or not logical_id:
            raise ValueError("manifest contains a record without an ID")
        if logical_id in records:
            raise ValueError(f"manifest contains duplicate ID {logical_id}")
        records[logical_id] = record

    changes: list[tuple[dict[str, Any], str]] = []
    skipped = 0
    for row in rows:
        origin = row["origin"]
        logical_id = row["id"]
        record = records.get(logical_id)
        if record is None:
            raise ValueError(f"{origin}: unknown manifest ID {logical_id}")
        manifest_source = record.get("source")
        manifest_hash = record.get("source_hash")
        if not isinstance(manifest_source, str) or not isinstance(manifest_hash, str):
            raise ValueError(f"{logical_id}: manifest source metadata is invalid")
        if row["source_hash"] != manifest_hash:
            raise ValueError(f"{origin}: source hash differs from manifest")
        if row["source"] != manifest_source:
            raise ValueError(f"{origin}: source differs from manifest")
        if source_hash(manifest_source) != manifest_hash:
            raise ValueError(f"{logical_id}: manifest source hash is invalid")

        verdict = row["verdict"]
        if verdict not in VERDICTS:
            raise ValueError(f"{origin}: invalid verdict {verdict!r}")
        recommendation = row["recommendation"]
        if verdict == "LENGTH_EXCEPTION" and not recommendation.strip():
            raise ValueError(
                f"{origin}: LENGTH_EXCEPTION requires a recommendation"
            )
        if verdict != "CHANGE":
            skipped += 1
            continue

        if not recommendation.strip():
            raise ValueError(f"{origin}: CHANGE requires a recommendation")
        output_format = record.get("output_format")
        if output_format not in SUPPORTED_FORMATS:
            raise ValueError(
                f"{origin}: unsupported output format {output_format!r}"
            )
        changes.append((record, recommendation))
    return changes, skipped


def _relative_output(path: Any) -> Path:
    if not isinstance(path, str) or not path:
        raise ValueError(f"unsafe override path {path!r}")
    relative = Path(path)
    if relative.is_absolute() or ".." in relative.parts:
        raise ValueError(f"unsafe override path {path!r}")
    return relative


def _load_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8-sig"))


def _native_patchouli_document(
    instance_root: Path, location: dict[str, Any]
) -> Any:
    archive_relative = _relative_output(location.get("archive"))
    archive_path = instance_root / archive_relative
    member = location.get("output_member")
    if not isinstance(member, str) or not member:
        raise ValueError("Patchouli location has no output member")
    with zipfile.ZipFile(archive_path) as archive:
        nested_member = location.get("nested_archive")
        if nested_member is None:
            data = archive.read(member)
        else:
            with zipfile.ZipFile(io.BytesIO(archive.read(nested_member))) as nested:
                data = nested.read(member)
    return json.loads(data.decode("utf-8-sig"))


def expected_override_files(
    version_root: Path,
    instance_root: Path,
    changes: list[tuple[dict[str, Any], str]],
) -> dict[Path, bytes]:
    overrides = version_root / "translation-overrides"
    language: dict[Path, dict[str, str]] = defaultdict(dict)
    patchouli: dict[Path, list[tuple[dict[str, Any], str]]] = defaultdict(list)

    for record, recommendation in changes:
        if record["output_format"] == "lang":
            namespace = record.get("namespace")
            key = record.get("translation_key")
            if not isinstance(namespace, str) or not namespace or "/" in namespace:
                raise ValueError(f"{record['id']}: invalid language namespace")
            if not isinstance(key, str) or not key:
                raise ValueError(f"{record['id']}: missing translation key")
            relative = Path("assets") / namespace / "lang/ru_ru.json"
            previous = language[relative].get(key)
            if previous is not None and previous != recommendation:
                raise ValueError(f"{key}: conflicting review recommendations")
            language[relative][key] = recommendation
        else:
            location = record.get("location")
            if not isinstance(location, dict):
                raise ValueError(f"{record['id']}: missing Patchouli location")
            relative = _relative_output(location.get("output_member", ""))
            if not relative.parts or relative.parts[0] != "assets":
                raise ValueError(f"{record['id']}: invalid Patchouli output member")
            patchouli[relative].append((record, recommendation))

    expected: dict[Path, bytes] = {}
    for relative, translations in sorted(language.items()):
        target = overrides / relative
        document = _load_json(target) if target.exists() else {}
        if not isinstance(document, dict):
            raise ValueError(f"{target}: language override must be a JSON object")
        for key, recommendation in sorted(translations.items()):
            document[key] = recommendation
        expected[relative] = json_bytes(document)

    for relative, records in sorted(patchouli.items()):
        target = overrides / relative
        if target.exists():
            document = _load_json(target)
        else:
            built = version_root / "payload/resourcepack" / relative
            if built.exists():
                document = _load_json(built)
            else:
                locations = [record["location"] for record, _ in records]
                bases = {
                    (
                        location.get("archive"),
                        location.get("nested_archive"),
                        location.get("output_member"),
                    )
                    for location in locations
                }
                if len(bases) != 1:
                    raise ValueError(
                        f"{relative}: conflicting Patchouli source locations"
                    )
                document = _native_patchouli_document(
                    instance_root, locations[0]
                )
        for record, recommendation in records:
            pointer = record["location"].get("pointer")
            if not isinstance(pointer, str) or not pointer.startswith("/"):
                raise ValueError(f"{record['id']}: invalid JSON pointer")
            json_pointer_set(document, pointer, recommendation)
        expected[relative] = json_bytes(document)
    return expected


def apply_review(
    version_root: Path,
    instance_root: Path,
    review_dir: Path,
    *,
    check: bool = False,
) -> ApplyCounts:
    manifest_path = version_root / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    rows = _review_rows(review_dir)
    changes, skipped = _validated_changes(manifest, rows)
    expected = expected_override_files(version_root, instance_root, changes)
    overrides = version_root / "translation-overrides"

    if check:
        for relative, content in expected.items():
            target = overrides / relative
            if not target.exists() or target.read_bytes() != content:
                raise ValueError(f"{target}: override is not current")
    else:
        for relative, content in expected.items():
            target = overrides / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(content)
    return ApplyCounts(len(changes), skipped, len(expected))


def parse_args() -> argparse.Namespace:
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", required=True)
    parser.add_argument("--instance-root", type=Path, required=True)
    parser.add_argument("--review-dir", type=Path, required=True)
    parser.add_argument(
        "--versions-root", type=Path, default=root / "translation-versions"
    )
    parser.add_argument("--check", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    version_root = args.versions_root.resolve() / args.version
    manifest = json.loads((version_root / "manifest.json").read_text(encoding="utf-8"))
    if manifest.get("version") != args.version:
        raise ValueError("manifest version does not match requested version")
    counts = apply_review(
        version_root,
        args.instance_root.resolve(),
        args.review_dir.resolve(),
        check=args.check,
    )
    print(
        f"applied={counts.applied} skipped={counts.skipped} files={counts.files}"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (
        OSError,
        ValueError,
        KeyError,
        csv.Error,
        json.JSONDecodeError,
        zipfile.BadZipFile,
    ) as exc:
        print(f"error: {exc}")
        raise SystemExit(2)
