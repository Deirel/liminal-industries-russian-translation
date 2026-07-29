#!/usr/bin/env python3
"""Build a quest-item language inventory from the installed modpack."""

from __future__ import annotations

import argparse
import csv
import json
import re
import zipfile
from collections.abc import Iterable
from pathlib import Path


ITEM_RE = re.compile(r'\bitem:\s*"([a-z0-9_.-]+:[a-z0-9_./-]+)"')
LANG_KEY_PREFIXES = (
    "item.",
    "block.",
    "fluid.",
    "fluid_type.",
    "entity.",
)


def read_json(data: bytes, source: str) -> dict[str, str]:
    if not data.strip():
        return {}
    try:
        parsed = json.loads(data.decode("utf-8-sig"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ValueError(f"Invalid language JSON in {source}: {exc}") from exc
    return {str(key): str(value) for key, value in parsed.items()}


def load_jar_languages(
    mods_dir: Path,
    jar_paths: Iterable[Path] | None = None,
) -> tuple[dict[str, str], dict[str, str]]:
    en_us: dict[str, str] = {}
    ru_ru: dict[str, str] = {}
    archives = sorted(jar_paths) if jar_paths is not None else sorted(mods_dir.glob("*.jar"))
    for jar_path in archives:
        try:
            with zipfile.ZipFile(jar_path) as jar:
                for member in jar.namelist():
                    target = None
                    if re.fullmatch(r"assets/[^/]+/lang/en_us\.json", member):
                        target = en_us
                    elif re.fullmatch(r"assets/[^/]+/lang/ru_ru\.json", member):
                        target = ru_ru
                    if target is not None:
                        target.update(read_json(jar.read(member), f"{jar_path}:{member}"))
        except zipfile.BadZipFile:
            continue
    return en_us, ru_ru


def load_item_model_ids(archives: list[Path], kubejs_dir: Path) -> set[str]:
    ids: set[str] = set()
    pattern = re.compile(r"assets/([^/]+)/models/item/(.+)\.json$")
    for archive in archives:
        try:
            with zipfile.ZipFile(archive) as jar:
                for member in jar.namelist():
                    match = pattern.fullmatch(member)
                    if match:
                        ids.add(f"{match.group(1)}:{match.group(2)}")
        except zipfile.BadZipFile:
            continue
    for base in (kubejs_dir, *sorted((kubejs_dir / "contentpacks").glob("*"))):
        for path in base.glob("assets/*/models/item/**/*.json"):
            relative = path.relative_to(base / "assets")
            namespace = relative.parts[0]
            item_path = Path(*relative.parts[3:]).with_suffix("").as_posix()
            ids.add(f"{namespace}:{item_path}")
    return ids


def load_probe_item_ids(kubejs_dir: Path) -> set[str]:
    globals_path = kubejs_dir / "probe/generated/globals.d.ts"
    if not globals_path.exists():
        return set()
    for line in globals_path.read_text(encoding="utf-8").splitlines():
        if line.startswith("    type Item = "):
            return set(re.findall(r'"([a-z0-9_.-]+:[a-z0-9_./-]+)"', line))
    return set()


def load_minecraft_languages(
    launcher_dir: Path, en_us: dict[str, str], ru_ru: dict[str, str]
) -> None:
    version_jar = launcher_dir / "versions/1.20.1/1.20.1.jar"
    if version_jar.exists():
        with zipfile.ZipFile(version_jar) as jar:
            member = "assets/minecraft/lang/en_us.json"
            en_us.update(read_json(jar.read(member), f"{version_jar}:{member}"))

    indexes_dir = launcher_dir / "assets/indexes"
    objects_dir = launcher_dir / "assets/objects"
    for index_path in sorted(indexes_dir.glob("*.json")):
        index = json.loads(index_path.read_text(encoding="utf-8"))
        entry = index.get("objects", {}).get("minecraft/lang/ru_ru.json")
        if not entry:
            continue
        digest = entry["hash"]
        object_path = objects_dir / digest[:2] / digest
        if object_path.exists():
            ru_ru.update(read_json(object_path.read_bytes(), str(object_path)))
            break


def overlay_loose_languages(kubejs_dir: Path, language: str, target: dict[str, str]) -> None:
    patterns = (
        f"assets/*/lang/{language}.json",
        f"contentpacks/*/assets/*/lang/{language}.json",
    )
    for pattern in patterns:
        for path in sorted(kubejs_dir.glob(pattern)):
            target.update(read_json(path.read_bytes(), str(path)))


def overlay_resource_pack(pack_dir: Path, language: str, target: dict[str, str]) -> None:
    for path in sorted(pack_dir.glob(f"assets/*/lang/{language}.json")):
        target.update(read_json(path.read_bytes(), str(path)))


def extract_quest_ids(quests_dir: Path) -> set[str]:
    ids: set[str] = set()
    for path in quests_dir.rglob("*.snbt"):
        ids.update(ITEM_RE.findall(path.read_text(encoding="utf-8")))
    return ids


def candidate_keys(item_id: str) -> list[str]:
    namespace, path = item_id.split(":", 1)
    return [
        f"item.{namespace}.{path}",
        f"block.{namespace}.{path}",
        f"fluid.{namespace}.{path}",
        f"fluid_type.{namespace}.{path}",
        f"entity.{namespace}.{path}",
    ]


def same_source_aliases(
    item_id: str,
    translation_key: str,
    source: str,
    en_us: dict[str, str],
) -> list[str]:
    return [
        key
        for key in candidate_keys(item_id)
        if key != translation_key and en_us.get(key) == source
    ]


def resolve_key(item_id: str, en_us: dict[str, str], ru_ru: dict[str, str]) -> str:
    for key in candidate_keys(item_id):
        if key in en_us or key in ru_ru:
            return key
    return candidate_keys(item_id)[0]


def main() -> None:
    translation_root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--root",
        type=Path,
        required=True,
        help="unpacked or running modpack root containing data/",
    )
    parser.add_argument(
        "--minecraft-root",
        type=Path,
        default=Path.home() / "Library/Application Support/sklauncher",
    )
    parser.add_argument(
        "--resource-pack",
        type=Path,
        default=translation_root
        / "translation-versions/1.19.3-7-ae2-fix/payload/resourcepack",
        help="reviewed Russian resource pack for the selected source version",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("item-translation-work/quest_items.tsv"),
    )
    parser.add_argument(
        "--scope",
        choices=("quest", "all"),
        default="quest",
        help="quest IDs only, or every item/block key found in en_us",
    )
    args = parser.parse_args()
    root = args.root.resolve()
    resource_pack = args.resource_pack.resolve()
    output = (
        args.output
        if args.output.is_absolute()
        else translation_root / args.output
    )

    en_us, ru_ru = load_jar_languages(root / "data/mods")
    load_minecraft_languages(args.minecraft_root, en_us, ru_ru)
    overlay_loose_languages(root / "data/kubejs", "en_us", en_us)
    overlay_loose_languages(root / "data/kubejs", "ru_ru", ru_ru)
    overlay_resource_pack(resource_pack, "ru_ru", ru_ru)
    quest_ids = extract_quest_ids(root / "data/config/ftbquests/quests")

    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle, delimiter="\t", lineterminator="\n")
        writer.writerow(("item_id", "translation_key", "en_us", "ru_ru", "status"))
        if args.scope == "quest":
            rows = [
                (item_id, resolve_key(item_id, en_us, ru_ru))
                for item_id in sorted(quest_ids)
            ]
        else:
            registry_ids = load_probe_item_ids(root / "data/kubejs")
            if not registry_ids:
                archives = sorted((root / "data/mods").glob("*.jar"))
                minecraft_jar = args.minecraft_root / "versions/1.20.1/1.20.1.jar"
                if minecraft_jar.exists():
                    archives.append(minecraft_jar)
                registry_ids = load_item_model_ids(archives, root / "data/kubejs")
            rows = [
                (item_id, resolve_key(item_id, en_us, ru_ru))
                for item_id in sorted(registry_ids)
            ]
        for item_id, key in rows:
            english = en_us.get(key, "")
            russian = ru_ru.get(key, "")
            if not russian:
                status = "MISSING_RU"
            elif russian == english and english:
                status = "RU_EQUALS_EN"
            else:
                status = "HAS_RU"
            writer.writerow((item_id, key, english, russian, status))

    missing = sum(1 for _, key in rows if not ru_ru.get(key, ""))
    print(f"Wrote {len(rows)} {args.scope} item/block rows to {output}")
    print(f"Missing Russian values: {missing}")


if __name__ == "__main__":
    main()
