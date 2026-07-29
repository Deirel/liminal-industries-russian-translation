import io
import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from translation_sources import (
    SourceDefinition,
    collect_patchouli,
    load_source_definitions,
)
from build_version_resources import build_patchouli_files
from build_initial_catalog import sha256_bytes


class SourceConfigurationTest(unittest.TestCase):
    def test_loads_ordered_source_definitions(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "sources.json"
            path.write_text(
                json.dumps(
                    {
                        "schema": 1,
                        "sources": [
                            {"id": "items", "adapter": "registry_items"},
                            {
                                "id": "patchouli",
                                "adapter": "patchouli",
                                "audit": True,
                            },
                        ],
                    }
                ),
                encoding="utf-8",
            )

            definitions = load_source_definitions(path)

        self.assertEqual(["items", "patchouli"], [value.source_id for value in definitions])
        self.assertEqual({"audit": True}, definitions[1].options)


class PatchouliSourceTest(unittest.TestCase):
    def test_collects_language_keys_and_literal_json_fields(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            instance = Path(directory)
            mods = instance / "mods"
            mods.mkdir()
            archive = mods / "guide.jar"
            with zipfile.ZipFile(archive, "w") as jar:
                jar.writestr(
                    "assets/example/patchouli_books/guide/en_us/categories/main.json",
                    json.dumps(
                        {
                            "name": "guide.category.main",
                            "description": "Literal description",
                            "icon": "minecraft:book",
                        }
                    ),
                )
                jar.writestr(
                    "data/example/patchouli_books/guide/book.json",
                    json.dumps(
                        {
                            "name": "guide.name",
                            "landing_text": "Literal landing text",
                        }
                    ),
                )

            result = collect_patchouli(
                SourceDefinition("patchouli", "patchouli", {}),
                [archive],
                instance,
                {
                    "guide.category.main": "Main category",
                    "guide.name": "Guide",
                },
                {},
            )

        by_kind = {}
        for record in result.records:
            by_kind.setdefault(record["kind"], []).append(record)
        self.assertEqual(2, len(by_kind["patchouli_book_metadata"]))
        self.assertEqual(1, len(by_kind["patchouli_language_text"]))
        self.assertEqual(1, len(by_kind["patchouli_json_text"]))
        direct = by_kind["patchouli_json_text"][0]
        self.assertEqual("Literal description", direct["source"])
        self.assertEqual(
            "assets/example/patchouli_books/guide/ru_ru/categories/main.json",
            direct["location"]["output_member"],
        )

    def test_collects_and_builds_patchouli_from_nested_jar(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            instance = Path(directory)
            mods = instance / "mods"
            mods.mkdir()
            archive = mods / "outer.jar"
            nested_member = "META-INF/jarjar/guide-core.jar"
            member = (
                "assets/example/patchouli_books/guide/en_us/entries/start.json"
            )
            source = json.dumps({"text": "Nested welcome"}).encode()
            native_member = member.replace("/en_us/", "/ru_ru/")
            source = json.dumps(
                {"name": "Start", "text": "Nested welcome"}
            ).encode()
            nested_bytes = io.BytesIO()
            with zipfile.ZipFile(nested_bytes, "w") as nested:
                nested.writestr(member, source)
                nested.writestr(
                    native_member,
                    json.dumps({"name": "Начало"}),
                )
            with zipfile.ZipFile(archive, "w") as jar:
                jar.writestr(nested_member, nested_bytes.getvalue())

            result = collect_patchouli(
                SourceDefinition("patchouli", "patchouli", {}),
                [archive],
                instance,
                {},
                {},
            )
            record = next(
                value
                for value in result.records
                if value["kind"] == "patchouli_json_text"
                and not value["native_ru_present"]
            )
            manifest = {
                "records": [record],
                "source_files": result.source_files,
            }
            catalog = {
                "entries": {
                    record["id"]: [
                        {
                            "source": record["source"],
                            "source_hash": record["source_hash"],
                            "translation": "Вложенное приветствие",
                        }
                    ]
                }
            }
            output = instance / "output"

            count = build_patchouli_files(
                manifest,
                catalog,
                instance,
                output,
            )
            built = json.loads(
                (
                    output
                    / "assets/example/patchouli_books/guide/ru_ru/entries/start.json"
                ).read_text(encoding="utf-8")
            )

        self.assertEqual(nested_member, record["location"]["nested_archive"])
        self.assertEqual(1, count)
        self.assertEqual("Начало", built["name"])
        self.assertEqual("Вложенное приветствие", built["text"])

    def test_builds_localized_patchouli_json(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            instance = Path(directory)
            mods = instance / "mods"
            mods.mkdir()
            archive = mods / "guide.jar"
            member = (
                "assets/example/patchouli_books/guide/en_us/entries/start.json"
            )
            source = json.dumps(
                {
                    "name": "Start",
                    "pages": [{"type": "patchouli:text", "text": "Welcome"}],
                }
            ).encode()
            with zipfile.ZipFile(archive, "w") as jar:
                jar.writestr(member, source)
            record = {
                "id": "patchouli-json:example:guide:entries/start.json:/pages/0/text",
                "kind": "patchouli_json_text",
                "source": "Welcome",
                "source_hash": "welcome-hash",
                "native_ru_present": False,
                "output_format": "patchouli_json",
                "location": {
                    "archive": "mods/guide.jar",
                    "member": member,
                    "output_member": member.replace("/en_us/", "/ru_ru/"),
                    "pointer": "/pages/0/text",
                },
            }
            manifest = {
                "records": [record],
                "source_files": [
                    {
                        "path": f"mods/guide.jar!/{member}",
                        "sha256": sha256_bytes(source),
                    }
                ],
            }
            catalog = {
                "entries": {
                    record["id"]: [
                        {
                            "source": "Welcome",
                            "source_hash": "welcome-hash",
                            "translation": "Добро пожаловать",
                        }
                    ]
                }
            }
            output = instance / "output"

            count = build_patchouli_files(
                manifest,
                catalog,
                instance,
                output,
            )
            built = json.loads(
                (
                    output
                    / "assets/example/patchouli_books/guide/ru_ru/entries/start.json"
                ).read_text(encoding="utf-8")
            )

        self.assertEqual(1, count)
        self.assertEqual("Добро пожаловать", built["pages"][0]["text"])
        self.assertEqual("Start", built["name"])


if __name__ == "__main__":
    unittest.main()
