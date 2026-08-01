from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from build_book_translation_index import build_index, json_bytes


class BookTranslationIndexTest(unittest.TestCase):
    def test_builds_sorted_index_for_each_book_format(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            resourcepack = Path(temp)
            fixtures = {
                "assets/example/patchouli_books/guide/ru_ru/entry.json": {
                    "title": "Патчули",
                },
                "assets/tconstruct/book/guide/ru_ru/page.json": {
                    "text": "Мантия",
                },
            }
            for relative, document in fixtures.items():
                target = resourcepack / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(json_bytes(document))
            manual = resourcepack / "assets/immersiveengineering/manual/ru_ru/a.txt"
            manual.parent.mkdir(parents=True, exist_ok=True)
            manual.write_text("Руководство\n", encoding="utf-8")
            language = resourcepack / "assets/tconstruct/book/guide/ru_ru/language.lang"
            language.parent.mkdir(parents=True, exist_ok=True)
            language.write_text("book.title=Книга\n", encoding="utf-8")

            manifest = {
                "source_files": [
                    {"path": "mods/a.jar!/assets/example/patchouli_books/guide/en_us/entry.json", "sha256": "a"},
                    {"path": "mods/b.jar!/assets/immersiveengineering/manual/en_us/a.txt", "sha256": "b"},
                    {"path": "mods/c.jar!/assets/tconstruct/book/guide/en_us/page.json", "sha256": "c"},
                    {"path": "mods/c.jar!/assets/tconstruct/book/guide/en_us/language.lang", "sha256": "d"},
                ],
                "records": [
                    self.record(
                        "patchouli_json",
                        "mods/a.jar",
                        "assets/example/patchouli_books/guide/en_us/entry.json",
                        "assets/example/patchouli_books/guide/ru_ru/entry.json",
                        "Hello",
                        pointer="/title",
                    ),
                    self.record(
                        "manual_text",
                        "mods/b.jar",
                        "assets/immersiveengineering/manual/en_us/a.txt",
                        "assets/immersiveengineering/manual/ru_ru/a.txt",
                        "Manual",
                        line=0,
                    ),
                    self.record(
                        "mantle_book_json",
                        "mods/c.jar",
                        "assets/tconstruct/book/guide/en_us/page.json",
                        "assets/tconstruct/book/guide/ru_ru/page.json",
                        "Mantle",
                        pointer="/text",
                    ),
                    self.record(
                        "mantle_book_language",
                        "mods/c.jar",
                        "assets/tconstruct/book/guide/en_us/language.lang",
                        "assets/tconstruct/book/guide/ru_ru/language.lang",
                        "Book",
                        key="book.title",
                    ),
                ],
            }

            first = build_index(manifest, resourcepack)
            second = build_index(manifest, resourcepack)

            self.assertEqual(first, second)
            self.assertEqual(4, len(first["resources"]))
            self.assertEqual(
                ["json", "lines", "properties", "json"],
                [resource["format"] for resource in first["resources"]],
            )

    def test_uses_compatibility_english_for_language_field_source(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            resourcepack = root / "resourcepack"
            compatibility = root / "compatibility"
            russian = resourcepack / "assets/example/lang/ru_ru.json"
            english = compatibility / "assets/example/lang/en_us.json"
            russian.parent.mkdir(parents=True)
            english.parent.mkdir(parents=True)
            russian.write_text(
                json.dumps({"book.page": "Текущий перевод"}),
                encoding="utf-8",
            )
            english.write_text(
                json.dumps({"book.page": "Current source"}),
                encoding="utf-8",
            )
            manifest = {
                "source_files": [],
                "records": [
                    {
                        "id": "patchouli-lang:example:book.page",
                        "source": "Old source",
                        "source_id": "patchouli",
                        "output_format": "lang",
                        "namespace": "example",
                        "translation_key": "book.page",
                        "native_ru_present": False,
                    }
                ],
            }

            index = build_index(manifest, resourcepack, compatibility)

        self.assertEqual(
            "Current source",
            index["resources"][0]["fields"][0]["source"],
        )

    def test_allows_exact_translation_to_shorten_json_array(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            resourcepack = Path(temp)
            output = resourcepack / "assets/tconstruct/book/guide/ru_ru/page.json"
            output.parent.mkdir(parents=True)
            output.write_bytes(json_bytes({"properties": ["Первое"]}))
            manifest = {
                "source_files": [
                    {
                        "path": "mods/c.jar!/assets/tconstruct/book/guide/en_us/page.json",
                        "sha256": "c",
                    }
                ],
                "records": [
                    self.record(
                        "mantle_book_json",
                        "mods/c.jar",
                        "assets/tconstruct/book/guide/en_us/page.json",
                        "assets/tconstruct/book/guide/ru_ru/page.json",
                        "First",
                        pointer="/properties/0",
                    ),
                    self.record(
                        "mantle_book_json",
                        "mods/c.jar",
                        "assets/tconstruct/book/guide/en_us/page.json",
                        "assets/tconstruct/book/guide/ru_ru/page.json",
                        "Second",
                        pointer="/properties/1",
                    ),
                ],
            }

            index = build_index(manifest, resourcepack)

        resource = index["resources"][0]
        self.assertTrue(resource["exact_only"])
        self.assertEqual([], resource["fields"])
        self.assertEqual(
            [
                "mantle_book_json:First",
                "mantle_book_json:Second",
            ],
            resource["field_ids"],
        )

    @staticmethod
    def record(
        output_format: str,
        archive: str,
        member: str,
        output_member: str,
        source: str,
        **address: object,
    ) -> dict[str, object]:
        return {
            "id": f"{output_format}:{source}",
            "source": source,
            "source_hash": "sha256:test",
            "output_format": output_format,
            "location": {
                "archive": archive,
                "member": member,
                "output_member": output_member,
                **address,
            },
        }


if __name__ == "__main__":
    unittest.main()
