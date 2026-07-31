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
