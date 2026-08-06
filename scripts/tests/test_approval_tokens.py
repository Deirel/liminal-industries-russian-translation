import sys
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from approve_version_translations import (
    TOKEN_RE,
    technical_tokens,
    technical_tokens_compatible,
    validate_block_item_consistency,
    apply_corrections,
    add_translations,
)
from catalog_utils import catalog_entries_hash, source_hash, validate_catalog


class ApprovalTokenTest(unittest.TestCase):
    def test_allows_existing_tokens_or_terminal_style_reset(self) -> None:
        self.assertTrue(technical_tokens_compatible("$(o)Source", "$(o)Перевод$(0)"))
        self.assertTrue(
            technical_tokens_compatible(
                "$(l:path)Source$(/l)", "$(item)Новый$(0)", "$(item)Старый$(0)"
            )
        )
        self.assertFalse(technical_tokens_compatible("Source", "$(item)Новый$(0)"))

    def test_preserves_patchouli_markup(self) -> None:
        source = "$(l:guide/start)Open$(/l) <item>Manual<r><n>"
        translation = "$(l:guide/start)Открыть$(/l) <item>Руководство<r><n>"

        self.assertEqual(
            TOKEN_RE.findall(source),
            TOKEN_RE.findall(translation),
        )

    def test_detects_changed_patchouli_markup(self) -> None:
        source = "$(item)Tree$(0)$(p)Text"
        translation = "$(item)Дерево$(0)Текст"

        self.assertNotEqual(
            TOKEN_RE.findall(source),
            TOKEN_RE.findall(translation),
        )

    def test_allows_translated_manual_link_label(self) -> None:
        source = "<&image>Use the <link;wiring;wire;page>."
        translation = "<&image>Используйте <link;wiring;провод;page>."

        self.assertEqual(
            technical_tokens(source),
            technical_tokens(translation),
        )

    def test_detects_changed_manual_link_target(self) -> None:
        source = "<link;wiring;wire;page>"
        translation = "<link;power;провод;page>"

        self.assertNotEqual(
            technical_tokens(source),
            technical_tokens(translation),
        )


class BlockItemConsistencyTest(unittest.TestCase):
    def setUp(self) -> None:
        self.item = {
            "id": "item:item.example.machine",
            "kind": "item_name",
            "item_id": "example:machine",
            "source": "Machine",
            "source_hash": "sha256:machine",
        }
        self.block = {
            "id": "block:block.example.machine",
            "kind": "block_name",
            "block_id": "example:machine",
            "source": "Machine",
            "source_hash": "sha256:machine",
        }
        self.records = {
            self.item["id"]: self.item,
            self.block["id"]: self.block,
        }
        self.catalog = {
            "entries": {
                self.item["id"]: [
                    {
                        "source": "Machine",
                        "source_hash": "sha256:machine",
                        "translation": "Машина",
                    }
                ]
            }
        }

    def test_accepts_block_translation_matching_immutable_item(self) -> None:
        validate_block_item_consistency(
            self.records,
            {
                self.block["id"]: {
                    "source_hash": "sha256:machine",
                    "translation": "Машина",
                }
            },
            self.catalog,
        )

    def test_rejects_block_translation_differing_from_immutable_item(self) -> None:
        with self.assertRaisesRegex(
            ValueError, "must match item translation 'Машина'"
        ):
            validate_block_item_consistency(
                self.records,
                {
                    self.block["id"]: {
                        "source_hash": "sha256:machine",
                        "translation": "Механизм",
                    }
                },
                self.catalog,
            )

    def test_uses_item_translation_from_same_review_batch(self) -> None:
        translations = {
            self.item["id"]: {
                "source_hash": "sha256:machine",
                "translation": "Машина",
            },
            self.block["id"]: {
                "source_hash": "sha256:machine",
                "translation": "Механизм",
            },
        }
        with self.assertRaisesRegex(ValueError, "must match item translation"):
            validate_block_item_consistency(
                self.records, translations, {"entries": {}}
            )


class CatalogCorrectionTest(unittest.TestCase):
    def setUp(self) -> None:
        self.record = {
            "id": "item:item.example.machine",
            "source": "Machine",
            "source_hash": source_hash("Machine"),
        }
        self.catalog = {
            "schema_version": 1,
            "entries": {
                self.record["id"]: [
                    {
                        "source": "Machine",
                        "source_hash": source_hash("Machine"),
                        "translation": "Механизм",
                    }
                ]
            },
            "corrections": [],
        }
        self.catalog["entries_hash"] = catalog_entries_hash(self.catalog["entries"])

    def correction(self, **changes: str) -> dict[str, str]:
        row = {
            "id": self.record["id"],
            "source_hash": self.record["source_hash"],
            "old_translation": "Механизм",
            "new_translation": "Машина",
            "reason": "Точнее соответствует контексту",
            "independent_review": "APPROVED",
        }
        row.update(changes)
        return row

    def test_applies_independently_reviewed_correction(self) -> None:
        self.assertEqual(
            1,
            apply_corrections(
                self.catalog,
                {self.record["id"]: self.record},
                [self.correction()],
            ),
        )
        self.assertEqual(
            "Машина",
            self.catalog["entries"][self.record["id"]][0]["translation"],
        )

    def test_adds_new_approved_translation(self) -> None:
        self.catalog["entries"] = {}
        self.assertEqual(
            1,
            add_translations(
                self.catalog,
                {self.record["id"]: self.record},
                {
                    self.record["id"]: {
                        "source_hash": self.record["source_hash"],
                        "translation": "Машина",
                    }
                },
            ),
        )
        self.assertEqual(
            "Машина",
            self.catalog["entries"][self.record["id"]][0]["translation"],
        )

    def test_rejects_correction_without_review(self) -> None:
        with self.assertRaisesRegex(ValueError, "lacks independent approval"):
            apply_corrections(
                self.catalog,
                {self.record["id"]: self.record},
                [self.correction(independent_review="")],
            )

    def test_rejects_invalid_manifest_source_hash(self) -> None:
        broken = dict(self.record, source_hash="sha256:broken")
        with self.assertRaisesRegex(ValueError, "invalid manifest source hash"):
            apply_corrections(
                self.catalog,
                {broken["id"]: broken},
                [self.correction(source_hash="sha256:broken")],
            )

    def test_rejects_silent_catalog_edit(self) -> None:
        self.catalog["entries"][self.record["id"]][0]["translation"] = "Тихая правка"
        with self.assertRaisesRegex(ValueError, "without an approval operation"):
            validate_catalog(self.catalog)

if __name__ == "__main__":
    unittest.main()
