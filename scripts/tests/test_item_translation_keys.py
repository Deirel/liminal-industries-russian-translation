import io
import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from build_version_resources import (
    apply_resourcepack_overrides,
    build_language_files,
)
from item_sources import (
    load_blockstate_ids,
    load_jar_languages,
    same_source_aliases,
)


class BlockRegistryDiscoveryTest(unittest.TestCase):
    def test_discovers_blockstate_without_language_key(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            archive = root / "example.jar"
            with zipfile.ZipFile(archive, "w") as jar:
                jar.writestr(
                    "assets/example/blockstates/hidden_machine.json",
                    '{"variants": {}}',
                )

            block_ids = load_blockstate_ids([archive], root / "kubejs")

        self.assertEqual({"example:hidden_machine"}, block_ids)


class NestedLanguageDiscoveryTest(unittest.TestCase):
    def test_loads_languages_from_nested_jar(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            mods = Path(directory)
            archive = mods / "outer.jar"
            nested_bytes = io.BytesIO()
            with zipfile.ZipFile(nested_bytes, "w") as nested:
                nested.writestr(
                    "assets/example/lang/en_us.json",
                    json.dumps(
                        {
                            "item.example.machine": "Machine",
                            "item.example.shared": "Core name",
                        }
                    ),
                )
                nested.writestr(
                    "assets/example/lang/ru_ru.json",
                    json.dumps({"item.example.machine": "Машина"}),
                )
            with zipfile.ZipFile(archive, "w") as jar:
                jar.writestr(
                    "META-INF/jarjar/example-core.jar",
                    nested_bytes.getvalue(),
                )
                jar.writestr(
                    "assets/example/lang/en_us.json",
                    json.dumps({"item.example.shared": "Outer name"}),
                )

            english, russian = load_jar_languages(mods)

        self.assertEqual("Machine", english["item.example.machine"])
        self.assertEqual("Outer name", english["item.example.shared"])
        self.assertEqual("Машина", russian["item.example.machine"])


class SameSourceAliasesTest(unittest.TestCase):
    def test_keeps_matching_block_key(self) -> None:
        aliases = same_source_aliases(
            "example:machine",
            "item.example.machine",
            "Machine",
            {
                "item.example.machine": "Machine",
                "block.example.machine": "Machine",
            },
        )

        self.assertEqual(["block.example.machine"], aliases)

    def test_rejects_key_with_different_source(self) -> None:
        aliases = same_source_aliases(
            "example:machine",
            "item.example.machine",
            "Machine",
            {
                "item.example.machine": "Machine",
                "block.example.machine": "Machine Block",
            },
        )

        self.assertEqual([], aliases)


class BuildItemAliasesTest(unittest.TestCase):
    def test_writes_primary_key_and_alias(self) -> None:
        record = {
            "id": "item:item.example.machine",
            "kind": "item_name",
            "source": "Machine",
            "source_hash": "hash",
            "namespace": "example",
            "translation_key": "item.example.machine",
            "translation_aliases": ["block.example.machine"],
            "native_ru_present": False,
            "output_format": "lang",
        }
        catalog = {
            "entries": {
                record["id"]: [
                    {
                        "source": record["source"],
                        "source_hash": record["source_hash"],
                        "translation": "Машина",
                    }
                ]
            }
        }

        with tempfile.TemporaryDirectory() as directory:
            count = build_language_files(
                {"records": [record]},
                catalog,
                {},
                Path(directory),
            )
            output = json.loads(
                (
                    Path(directory)
                    / "assets/example/lang/ru_ru.json"
                ).read_text(encoding="utf-8")
            )

        self.assertEqual(2, count)
        self.assertEqual("Машина", output["item.example.machine"])
        self.assertEqual("Машина", output["block.example.machine"])


class ResourcepackOverrideTest(unittest.TestCase):
    def test_merges_language_override_without_dropping_generated_keys(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            resourcepack = root / "resourcepack"
            generated = resourcepack / "assets/example/lang/ru_ru.json"
            generated.parent.mkdir(parents=True)
            generated.write_text(
                json.dumps(
                    {
                        "block.example.machine": "Машина",
                        "item.example.shared": "Старое название",
                    }
                ),
                encoding="utf-8",
            )
            overrides = root / "overrides"
            override = overrides / "assets/example/lang/ru_ru.json"
            override.parent.mkdir(parents=True)
            override.write_text(
                json.dumps(
                    {
                        "item.example.shared": "Новое название",
                        "item.example.extra": "Дополнение",
                    }
                ),
                encoding="utf-8",
            )

            apply_resourcepack_overrides(overrides, resourcepack)
            output = json.loads(generated.read_text(encoding="utf-8"))

        self.assertEqual("Машина", output["block.example.machine"])
        self.assertEqual("Новое название", output["item.example.shared"])
        self.assertEqual("Дополнение", output["item.example.extra"])


if __name__ == "__main__":
    unittest.main()
