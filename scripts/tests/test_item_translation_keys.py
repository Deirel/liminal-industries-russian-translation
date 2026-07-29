import json
import sys
import tempfile
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from build_version_resources import build_items
from extract_item_names import same_source_aliases


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
            count = build_items(
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


if __name__ == "__main__":
    unittest.main()
