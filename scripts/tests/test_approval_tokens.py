import sys
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from approve_version_translations import (
    TOKEN_RE,
    technical_tokens,
    validate_block_item_consistency,
)


class ApprovalTokenTest(unittest.TestCase):
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


if __name__ == "__main__":
    unittest.main()
