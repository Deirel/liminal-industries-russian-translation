#!/usr/bin/env python3
"""Build and verify an optional technical compatibility resource pack."""

from __future__ import annotations

import argparse
import tempfile
import zipfile
from pathlib import Path

from build_resource_packs import (
    load_json,
    slug,
    validate_book_recipe_references,
    verify_archives,
    write_archive,
)


def load_compatibility_resources(source: Path) -> dict[str, bytes]:
    metadata = source / "pack.mcmeta"
    assets = source / "assets"
    if not metadata.is_file() or not assets.is_dir():
        raise ValueError(
            f"{source}: compatibility pack requires pack.mcmeta and assets"
        )

    files: dict[str, bytes] = {}
    for path in sorted(source.rglob("*")):
        if not path.is_file():
            continue
        relative = path.relative_to(source).as_posix()
        if path.suffix in {".json", ".mcmeta"}:
            load_json(path)
        files[relative] = path.read_bytes()
    if not any(path.startswith("assets/") for path in files):
        raise ValueError(f"{source}: compatibility pack has no assets")
    validate_book_recipe_references(files)
    return files


def archive_name(version: str) -> str:
    return f"liminal-industries-compatibility-li{slug(version)}.zip"


def build_archive(
    destination: Path,
    version: str,
    source: Path,
    icon: bytes | None,
) -> Path:
    files = load_compatibility_resources(source)
    if icon is not None:
        files["pack.png"] = icon
    output = destination / archive_name(version)
    write_archive(output, files)
    return output


def parse_args() -> argparse.Namespace:
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", required=True)
    parser.add_argument(
        "--versions-root", type=Path, default=root / "translation-versions"
    )
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument(
        "--icon",
        type=Path,
        default=root / "assets/liminal-industries-russian-translation-256kb.png",
    )
    parser.add_argument(
        "--check", action="store_true", help="verify existing archive without changes"
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    version_root = args.versions_root.resolve() / args.version
    source = version_root / "compatibility-resourcepack"
    output = args.output.resolve()
    icon = args.icon.resolve().read_bytes() if args.icon is not None else None

    if args.check:
        if not output.is_dir():
            raise ValueError(f"compatibility pack output does not exist: {output}")
        with tempfile.TemporaryDirectory(
            prefix="liminal-compatibility-pack-"
        ) as directory:
            expected = Path(directory)
            build_archive(expected, args.version, source, icon)
            verify_archives(expected, output)
        print(f"Verified compatibility pack for {args.version}")
    else:
        output.mkdir(parents=True, exist_ok=True)
        for old_archive in output.glob("*.zip"):
            old_archive.unlink()
        archive = build_archive(output, args.version, source, icon)
        print(f"Built compatibility pack for {args.version}: {archive}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, TypeError, zipfile.BadZipFile) as exc:
        print(f"error: {exc}")
        raise SystemExit(2)
