#!/usr/bin/env python3
"""Build the runtime screenshot target list for checkpoint 13."""

from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path


VERSIONS = ("1.19.3-original", "1.19.3-7-ae2-fix")
RUNTIME_EXCLUSIONS = {
    "patchouli-lang:botania:botania.entry.gardenOfGlass",
    "patchouli-lang:botania:botania.page.gardenOfGlass0",
    "patchouli-lang:botania:botania.page.gardenOfGlass14",
}


def target(identifier: str, context: str) -> tuple[str, str, str] | None:
    if identifier.startswith(("item:", "block:")):
        return None
    if identifier.startswith("patchouli-lang:"):
        location = json.loads(context)["location"]
        resource = location["output_member"]
        pointer = location["pointer"]
        screen = ""
        if pointer.startswith("/pages/"):
            page = int(pointer.split("/", 3)[2])
            screen = f"/{page - page % 2}"
        return resource, "translation_key:" + identifier.rsplit(":", 1)[-1], screen
    if identifier.startswith("ie-manual:"):
        _, namespace, rest = identifier.split(":", 2)
        filename, line = rest.rsplit(":line:", 1)
        return (
            f"assets/{namespace}/manual/ru_ru/{filename}",
            "",
            "/1" if int(line) >= 4 else "/0",
        )
    if identifier.startswith("mantle-book:"):
        _, namespace, book, rest = identifier.split(":", 3)
        relative, pointer = rest.split(".json:", 1)
        return (
            f"assets/{namespace}/book/{book}/ru_ru/{relative}.json",
            "json_pointer:" + pointer,
            "",
        )
    raise ValueError(f"Unsupported book identifier: {identifier}")


def build(root: Path, version: str) -> tuple[str, str]:
    source = (
        root
        / "translation-versions"
        / version
        / "work"
        / "language-audit-2026-08-06"
        / "approved-changes.tsv"
    )
    rows = []
    excluded = []
    effective_path = root / "translation-versions" / version / "work" / "effective-translations.tsv"
    with effective_path.open(encoding="utf-8", newline="") as handle:
        contexts = {
            row["id"]: row["context"]
            for row in csv.DictReader(handle, delimiter="\t")
        }
    with source.open(encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle, delimiter="\t"):
            if row["id"] in RUNTIME_EXCLUSIONS:
                excluded.append(row["id"])
                continue
            value = target(row["id"], contexts[row["id"]])
            if value is not None:
                rows.append((row["id"], *value))
    lines = ["id\tresource\tsource\tscreen"]
    lines.extend("\t".join(row) for row in rows)
    exclusions = ["id\treason"]
    exclusions.extend(
        identifier
        + "\tRuntime-inaccessible: Botania entry flag |debug,mod:gardenofglass; "
        + "the installed pack is not a development environment and has no Garden of Glass mod."
        for identifier in excluded
    )
    return "\n".join(lines) + "\n", "\n".join(exclusions) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    output = root / "docs" / "translation-quality-audit"
    for version in VERSIONS:
        path = output / f"checkpoint-13-visual-targets-{version}.tsv"
        content, exclusions = build(root, version)
        exclusions_path = output / f"checkpoint-13-runtime-exclusions-{version}.tsv"
        if args.check:
            if not path.is_file() or path.read_text(encoding="utf-8") != content:
                raise SystemExit(f"visual targets are not current: {path}")
            if (
                not exclusions_path.is_file()
                or exclusions_path.read_text(encoding="utf-8") != exclusions
            ):
                raise SystemExit(f"runtime exclusions are not current: {exclusions_path}")
        else:
            path.write_text(content, encoding="utf-8")
            exclusions_path.write_text(exclusions, encoding="utf-8")
        print(f"{version}: {content.count(chr(10)) - 1} visual targets")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
