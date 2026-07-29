import json
import sys
import tempfile
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from build_resource_packs import (
    load_canonical,
    selected_resources,
    validate_manifest,
)


class ResourcePackSourceTest(unittest.TestCase):
    def test_partitions_language_and_patchouli_resources_together(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            language = root / "assets/example/lang/ru_ru.json"
            patchouli = (
                root
                / "assets/example/patchouli_books/guide/ru_ru/entries/start.json"
            )
            language.parent.mkdir(parents=True)
            patchouli.parent.mkdir(parents=True)
            language.write_text('{"item.example.guide": "Руководство"}', encoding="utf-8")
            patchouli.write_text('{"name": "Начало"}', encoding="utf-8")
            canonical = load_canonical(root)
            manifest = {
                "schema": 1,
                "translation_version": "test",
                "minecraft_version": "1.20.1",
                "packs": [
                    {
                        "id": "example",
                        "display_name": "Example",
                        "target_mod_versions": ["1"],
                        "namespaces": ["example"],
                    }
                ],
            }

            packs = validate_manifest(manifest, "test", canonical)
            selected = selected_resources(packs[0], canonical)

        self.assertEqual(
            {
                "assets/example/lang/ru_ru.json",
                "assets/example/patchouli_books/guide/ru_ru/entries/start.json",
            },
            set(selected),
        )
        self.assertEqual(
            {"name": "Начало"},
            json.loads(
                selected[
                    "assets/example/patchouli_books/guide/ru_ru/entries/start.json"
                ]
            ),
        )


if __name__ == "__main__":
    unittest.main()
