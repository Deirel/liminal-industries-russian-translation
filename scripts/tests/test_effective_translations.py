import json
import sys
import tempfile
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from export_effective_translations import effective_row


class EffectiveTranslationTest(unittest.TestCase):
    def test_reports_catalog_payload_and_native(self) -> None:
        record = {
            "id": "item:item.test.widget",
            "kind": "item_name",
            "source_id": "items",
            "source": "Widget",
            "source_hash": "hash",
            "namespace": "test",
            "translation_key": "item.test.widget",
            "output_format": "lang",
            "native_translation": "Штатный виджет",
        }
        catalog = {
            "entries": {
                record["id"]: [
                    {
                        "source_hash": "hash",
                        "source": "Widget",
                        "translation": "Виджет",
                    }
                ]
            }
        }
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            lang = root / "assets/test/lang/ru_ru.json"
            lang.parent.mkdir(parents=True)
            lang.write_text(
                json.dumps({"item.test.widget": "Виджет"}), encoding="utf-8"
            )

            row = effective_row(record, "test", catalog, root)
            self.assertEqual(
                (row["effective_translation"], row["origin"]),
                ("Виджет", "catalog"),
            )

            overrides = root / "overrides"
            override_lang = overrides / "assets/test/lang/ru_ru.json"
            override_lang.parent.mkdir(parents=True)
            override_lang.write_text(
                json.dumps({"item.test.widget": "Виджет"}), encoding="utf-8"
            )
            row = effective_row(record, "test", catalog, root, overrides)
            self.assertEqual(row["origin"], "translation-overrides")

            lang.write_text(
                json.dumps({"item.test.widget": "Исправленный виджет"}),
                encoding="utf-8",
            )
            override_lang.write_text(json.dumps({}), encoding="utf-8")
            row = effective_row(record, "test", catalog, root, overrides)
            self.assertEqual(
                (row["effective_translation"], row["origin"]),
                ("Исправленный виджет", "translation-overrides"),
            )

            lang.unlink()
            row = effective_row(record, "test", catalog, root, overrides)
            self.assertEqual(
                (row["effective_translation"], row["origin"]),
                ("Штатный виджет", "native_ru"),
            )


if __name__ == "__main__":
    unittest.main()
