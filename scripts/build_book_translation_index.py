#!/usr/bin/env python3
"""Build the deterministic runtime index for source-aware book translations."""

from __future__ import annotations

import argparse
import json
from collections import defaultdict
from pathlib import Path
from typing import Any

from translation_sources import json_pointer_get, parse_mantle_language


FORMATS = {
    "patchouli_json": "json",
    "manual_text": "lines",
    "mantle_book_json": "json",
    "mantle_book_language": "properties",
}


def json_bytes(value: Any) -> bytes:
    return (
        json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    ).encode("utf-8")


def resource_location(member: str) -> dict[str, str]:
    parts = member.split("/")
    if len(parts) < 3 or parts[0] != "assets":
        raise ValueError(f"{member}: expected an assets resource")
    return {"namespace": parts[1], "path": "/".join(parts[2:])}


def translated_value(
    output_format: str,
    output: Path,
    location: dict[str, Any],
) -> str:
    if output_format in {"patchouli_json", "mantle_book_json"}:
        document = json.loads(output.read_text(encoding="utf-8-sig"))
        value = json_pointer_get(document, location["pointer"])
    elif output_format == "manual_text":
        lines = output.read_text(encoding="utf-8-sig").splitlines()
        value = lines[location["line"]]
    elif output_format == "mantle_book_language":
        value = parse_mantle_language(
            output.read_bytes(),
            output.as_posix(),
        )[location["key"]]
    else:
        raise ValueError(f"unsupported book output format {output_format}")
    if not isinstance(value, str):
        raise ValueError(f"{output}: translated field is not a string")
    return value


def build_index(manifest: dict[str, Any], resourcepack: Path) -> dict[str, Any]:
    source_hashes = {
        entry["path"]: entry["sha256"]
        for entry in manifest["source_files"]
    }
    grouped: dict[
        tuple[str, str | None, str, str, str],
        list[dict[str, Any]],
    ] = defaultdict(list)
    for record in manifest["records"]:
        output_format = record.get("output_format")
        if output_format not in FORMATS:
            continue
        location = record["location"]
        grouped[
            (
                output_format,
                location.get("nested_archive"),
                location["archive"],
                location["member"],
                location["output_member"],
            )
        ].append(record)

    resources: list[dict[str, Any]] = []
    for (
        output_format,
        nested_archive,
        archive,
        member,
        output_member,
    ), records in sorted(
        grouped.items(),
        key=lambda item: tuple(value or "" for value in item[0]),
    ):
        output_location = resource_location(output_member)
        output = (
            resourcepack
            / "assets"
            / output_location["namespace"]
            / output_location["path"]
        )
        # Fully native resources are intentionally left to the target mod.
        if not output.is_file():
            continue
        source_label = archive
        if nested_archive is not None:
            source_label += f"!/{nested_archive}"
        source_label += f"!/{member}"
        source_sha256 = source_hashes.get(source_label)
        if source_sha256 is None:
            raise ValueError(f"{source_label}: source hash is missing")

        fields = []
        for record in sorted(records, key=lambda value: value["id"]):
            translation = translated_value(
                output_format,
                output,
                record["location"],
            )
            if translation == record["source"]:
                continue
            address_name = {
                "patchouli_json": "pointer",
                "mantle_book_json": "pointer",
                "manual_text": "line",
                "mantle_book_language": "key",
            }[output_format]
            fields.append(
                {
                    "id": record["id"],
                    "source": record["source"],
                    address_name: record["location"][address_name],
                    "translation": translation,
                }
            )
        if not fields:
            continue
        resources.append(
            {
                "format": FORMATS[output_format],
                "source": {
                    **resource_location(member),
                    "sha256": source_sha256,
                },
                "output": output_location,
                "fields": fields,
            }
        )

    patchouli_language = defaultdict(list)
    for record in manifest["records"]:
        if (
            record.get("source_id") == "patchouli"
            and record.get("output_format") == "lang"
            and not record.get("native_ru_present", False)
        ):
            patchouli_language[record["namespace"]].append(record)
    for namespace, records in sorted(patchouli_language.items()):
        output = resourcepack / "assets" / namespace / "lang/ru_ru.json"
        if not output.is_file():
            continue
        values = json.loads(output.read_text(encoding="utf-8"))
        fields = []
        for record in sorted(records, key=lambda value: value["id"]):
            key = record["translation_key"]
            translation = values.get(key)
            if not isinstance(translation, str):
                raise ValueError(f"{output}: missing Patchouli language key {key}")
            if translation == record["source"]:
                continue
            fields.append(
                {
                    "id": record["id"],
                    "key": key,
                    "source": record["source"],
                    "translation": translation,
                }
            )
        if fields:
            resources.append(
                {
                    "format": "language_json",
                    "source": {
                        "namespace": namespace,
                        "path": "lang/en_us.json",
                    },
                    "output": {
                        "namespace": namespace,
                        "path": "lang/ru_ru.json",
                    },
                    "fields": fields,
                }
            )

    resources.sort(
        key=lambda resource: (
            resource["output"]["namespace"],
            resource["output"]["path"],
        )
    )
    return {"schema": 1, "resources": resources}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--resourcepack", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    index = build_index(manifest, args.resourcepack)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(json_bytes(index))
    print(
        f"Indexed {len(index['resources'])} source-aware book resources "
        f"with {sum(len(resource['fields']) for resource in index['resources'])} fields"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, IndexError, json.JSONDecodeError) as exc:
        print(f"error: {exc}")
        raise SystemExit(2)
