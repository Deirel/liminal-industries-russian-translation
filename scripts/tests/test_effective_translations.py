import json
import sys
import tempfile
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from export_effective_translations import annotate_manifest, effective_row
from build_version_resources import build_language_files, unresolved_translation_ids


class EffectiveTranslationTest(unittest.TestCase):
    def test_build_blocks_unreviewed_native_translation(self) -> None:
        record = {
            "id": "item:item.test.new",
            "translation_status": "REVIEW_NATIVE",
            "native_ru_present": True,
        }
        self.assertEqual(
            [record["id"]],
            unresolved_translation_ids({"records": [record]}, {"entries": {}}),
        )

    def test_language_rebuild_drops_removed_payload_resource(self) -> None:
        record = {
            "id": "item:item.test.kept",
            "source": "Kept",
            "source_hash": "kept-hash",
            "namespace": "test",
            "translation_key": "item.test.kept",
            "output_format": "lang",
            "native_ru_present": False,
        }
        catalog = {
            "entries": {
                record["id"]: [
                    {
                        "source": record["source"],
                        "source_hash": record["source_hash"],
                        "translation": "Сохранено",
                    }
                ]
            }
        }
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            stale = output / "assets/deleted/lang/ru_ru.json"
            stale.parent.mkdir(parents=True)
            stale.write_text('{"item.deleted": "Удалено"}', encoding="utf-8")

            build_language_files({"records": [record]}, catalog, {}, output)

            self.assertFalse(stale.exists())
            self.assertTrue((output / "assets/test/lang/ru_ru.json").exists())

    def test_manifest_approval_requires_matching_reviewed_effective_text(self) -> None:
        row = {
            "id": "item:item.test.widget",
            "source_hash": "hash",
            "source": "Widget",
            "effective_translation": "Виджет",
            "context": '{"kind":"item_name"}',
            "version": "test",
            "origin": "native_ru",
        }
        manifest = {"records": [{"id": row["id"]}]}
        review = {
            **row,
            "verdict": "PASS",
            "recommendation": "",
            "independent_review": "",
        }

        annotated = annotate_manifest(manifest, [row], [review])

        self.assertTrue(
            annotated["records"][0]["effective_translation_approved"]
        )
        changed = [{**row, "effective_translation": "Другой текст"}]
        with self.assertRaisesRegex(ValueError, "reviewed translation changed"):
            annotate_manifest(manifest, changed, [review])

    def test_outputs_only_catalog_translation_that_differs_from_native(self) -> None:
        records = [
            {
                "id": "item:item.test.same",
                "source": "Same",
                "source_hash": "same-hash",
                "namespace": "test",
                "translation_key": "item.test.same",
                "output_format": "lang",
                "native_ru_present": True,
                "native_translation": "Одинаково",
            },
            {
                "id": "item:item.test.changed",
                "source": "Changed",
                "source_hash": "changed-hash",
                "namespace": "test",
                "translation_key": "item.test.changed",
                "output_format": "lang",
                "native_ru_present": True,
                "native_translation": "Штатно",
            },
        ]
        catalog = {
            "entries": {
                records[0]["id"]: [
                    {
                        "source": "Same",
                        "source_hash": "same-hash",
                        "translation": "Одинаково",
                    }
                ],
                records[1]["id"]: [
                    {
                        "source": "Changed",
                        "source_hash": "changed-hash",
                        "translation": "Исправлено",
                    }
                ],
            }
        }
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            self.assertEqual(
                1,
                build_language_files({"records": records}, catalog, {}, output),
            )
            values = json.loads(
                (output / "assets/test/lang/ru_ru.json").read_text(encoding="utf-8")
            )
            self.assertEqual({"item.test.changed": "Исправлено"}, values)

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
