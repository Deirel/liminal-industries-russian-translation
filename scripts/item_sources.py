"""Discover registry IDs and effective item translation keys."""

from __future__ import annotations

import io
import json
import re
import zipfile
from collections.abc import Iterable
from pathlib import Path


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
    archives = (
        sorted(jar_paths)
        if jar_paths is not None
        else sorted(mods_dir.glob("*.jar"))
    )
    for jar_path in archives:
        try:
            with zipfile.ZipFile(jar_path) as jar:
                containers: list[tuple[str | None, zipfile.ZipFile]] = []
                nested_handles: list[zipfile.ZipFile] = []
                for nested_member in jar.namelist():
                    if (
                        nested_member.startswith("META-INF/jarjar/")
                        and nested_member.endswith(".jar")
                    ):
                        nested = zipfile.ZipFile(io.BytesIO(jar.read(nested_member)))
                        nested_handles.append(nested)
                        containers.append((nested_member, nested))
                containers.append((None, jar))
                for nested_member, container in containers:
                    for member in container.namelist():
                        target = None
                        if re.fullmatch(r"assets/[^/]+/lang/en_us\.json", member):
                            target = en_us
                        elif re.fullmatch(r"assets/[^/]+/lang/ru_ru\.json", member):
                            target = ru_ru
                        if target is not None:
                            source = str(jar_path)
                            if nested_member is not None:
                                source += f":{nested_member}"
                            source += f":{member}"
                            target.update(read_json(container.read(member), source))
                for nested in nested_handles:
                    nested.close()
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


def load_blockstate_ids(archives: list[Path], kubejs_dir: Path) -> set[str]:
    ids: set[str] = set()
    pattern = re.compile(r"assets/([^/]+)/blockstates/(.+)\.json$")
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
        for path in base.glob("assets/*/blockstates/**/*.json"):
            relative = path.relative_to(base / "assets")
            namespace = relative.parts[0]
            block_path = Path(*relative.parts[2:]).with_suffix("").as_posix()
            ids.add(f"{namespace}:{block_path}")
    return ids


def load_probe_registry_ids(kubejs_dir: Path, registry: str) -> set[str]:
    globals_path = kubejs_dir / "probe/generated/globals.d.ts"
    if not globals_path.exists():
        return set()
    prefix = f"    type {registry.title()} = "
    for line in globals_path.read_text(encoding="utf-8").splitlines():
        if line.startswith(prefix):
            return set(re.findall(r'"([a-z0-9_.-]+:[a-z0-9_./-]+)"', line))
    return set()


def load_probe_item_ids(kubejs_dir: Path) -> set[str]:
    return load_probe_registry_ids(kubejs_dir, "item")


def load_probe_block_ids(kubejs_dir: Path) -> set[str]:
    return load_probe_registry_ids(kubejs_dir, "block")


def load_minecraft_languages(
    launcher_dir: Path,
    en_us: dict[str, str],
    ru_ru: dict[str, str],
    minecraft_version: str = "1.20.1",
) -> None:
    version_jar = (
        launcher_dir
        / "versions"
        / minecraft_version
        / f"{minecraft_version}.jar"
    )
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
