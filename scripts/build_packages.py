#!/usr/bin/env python3
"""Build deterministic resource-pack and quest-config packages."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import sys
import zipfile
from pathlib import Path, PurePosixPath


ROOT = Path(__file__).resolve().parents[1]
QUESTS = ROOT / "src" / "quests"
RESOURCE_PACK = ROOT / "src" / "resourcepack"
DIST = ROOT / "dist"
ZIP_TIMESTAMP = (1980, 1, 1, 0, 0, 0)
VERSION_PATTERN = re.compile(r"^[0-9A-Za-z][0-9A-Za-z._+-]*$")
DEFAULT_VERSION = "1.19.3-7-ae2-fix"


def unique_object(pairs: list[tuple[str, object]]) -> dict[str, object]:
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def read_json_object(path: Path) -> dict[str, object]:
    try:
        value = json.loads(
            path.read_text(encoding="utf-8"),
            object_pairs_hook=unique_object,
        )
    except (OSError, json.JSONDecodeError, ValueError) as error:
        raise ValueError(f"Invalid JSON in {path.relative_to(ROOT)}: {error}") from error
    if not isinstance(value, dict):
        raise ValueError(f"JSON file must contain an object: {path.relative_to(ROOT)}")
    return value


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build the Liminal Industries Russian translation packages."
    )
    parser.add_argument(
        "--version",
        default=DEFAULT_VERSION,
        help=(
            "Modpack version used in archive names "
            f"(default: {DEFAULT_VERSION})."
        ),
    )
    return parser.parse_args()


def source_files(directory: Path) -> list[Path]:
    files = []
    for path in directory.rglob("*"):
        if path.is_symlink():
            raise ValueError(f"Symbolic links are not allowed: {path}")
        if path.is_file():
            files.append(path)
    return sorted(files, key=lambda path: path.relative_to(directory).as_posix())


def validate_sources() -> None:
    required_quest_files = {
        QUESTS / "data.snbt",
        QUESTS / "chapter_groups.snbt",
    }
    missing = sorted(path for path in required_quest_files if not path.is_file())
    if missing:
        raise ValueError(
            "Missing required quest files: "
            + ", ".join(str(path.relative_to(ROOT)) for path in missing)
        )

    metadata_path = RESOURCE_PACK / "pack.mcmeta"
    metadata = read_json_object(metadata_path)
    pack_metadata = metadata.get("pack")
    if not isinstance(pack_metadata, dict) or not isinstance(
        pack_metadata.get("pack_format"), int
    ):
        raise ValueError("src/resourcepack/pack.mcmeta has no integer pack_format")

    language_files = source_files(RESOURCE_PACK / "assets")
    if not language_files:
        raise ValueError("The resource pack contains no language files")
    for path in language_files:
        if path.name != "ru_ru.json":
            raise ValueError(f"Unexpected resource-pack file: {path.relative_to(ROOT)}")
        read_json_object(path)


def zip_info(archive_path: PurePosixPath) -> zipfile.ZipInfo:
    info = zipfile.ZipInfo(archive_path.as_posix(), ZIP_TIMESTAMP)
    info.compress_type = zipfile.ZIP_DEFLATED
    info.external_attr = 0o100644 << 16
    info.create_system = 3
    return info


def add_file(
    archive: zipfile.ZipFile,
    source: Path,
    archive_path: PurePosixPath,
) -> None:
    archive.writestr(zip_info(archive_path), source.read_bytes())


def add_tree(
    archive: zipfile.ZipFile,
    source: Path,
    archive_root: PurePosixPath,
) -> None:
    for path in source_files(source):
        add_file(
            archive,
            path,
            archive_root / PurePosixPath(path.relative_to(source).as_posix()),
        )


def build_resource_pack(path: Path) -> None:
    with zipfile.ZipFile(
        path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9
    ) as archive:
        add_tree(archive, RESOURCE_PACK, PurePosixPath())


def build_quest_configs(path: Path) -> None:
    with zipfile.ZipFile(
        path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9
    ) as archive:
        add_tree(
            archive,
            QUESTS,
            PurePosixPath("config/ftbquests/quests"),
        )


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    args = parse_args()
    if not VERSION_PATTERN.fullmatch(args.version):
        print("error: --version contains unsupported characters", file=sys.stderr)
        return 2

    try:
        validate_sources()
    except ValueError as error:
        print(f"error: {error}", file=sys.stderr)
        return 1

    shutil.rmtree(DIST, ignore_errors=True)
    DIST.mkdir(parents=True)

    resource_pack = (
        DIST / f"liminal-industries-russian-resource-pack-{args.version}.zip"
    )
    quest_configs = (
        DIST / f"liminal-industries-russian-quest-configs-{args.version}.zip"
    )
    build_resource_pack(resource_pack)
    build_quest_configs(quest_configs)

    checksums = DIST / "SHA256SUMS"
    checksums.write_text(
        "".join(
            f"{sha256(path)}  {path.name}\n"
            for path in (resource_pack, quest_configs)
        ),
        encoding="ascii",
    )

    for path in (resource_pack, quest_configs, checksums):
        print(path.relative_to(ROOT))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
