import sys
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from build_quality_review import build_review


class QualityReviewTest(unittest.TestCase):
    def setUp(self) -> None:
        self.effective = [
            {
                "id": "item:item.test.widget",
                "source_hash": "hash",
                "source": "Widget",
                "effective_translation": "Виджет",
                "context": '{"state":"visible"}',
            }
        ]
        self.change = {
            "id": self.effective[0]["id"],
            "source_hash": "hash",
            "old_translation": "Виджет",
            "new_translation": "Штуковина",
            "reason": "Точнее в контексте",
            "independent_review": "APPROVED",
            "reviewer": "independent-reviewer",
            "initial_new_translation": "Штуковина",
            "review_notes": "APPROVED_AS_IS",
        }
        self.verdict = {
            "id": self.effective[0]["id"],
            "source_hash": "hash",
            "verdict": "CHANGE",
            "reviewer": "reviewer",
            "method": "source-context-review",
        }

    def test_records_reviewed_change(self) -> None:
        rows = build_review(self.effective, [self.change], [self.verdict])
        self.assertEqual(("CHANGE", "Штуковина"), (rows[0]["verdict"], rows[0]["recommendation"]))

    def test_rejects_unreviewed_change(self) -> None:
        with self.assertRaisesRegex(ValueError, "lacks independent review"):
            build_review(
                self.effective,
                [{**self.change, "independent_review": ""}],
                [self.verdict],
            )

    def test_rejects_missing_individual_verdict(self) -> None:
        with self.assertRaisesRegex(ValueError, "lacks an individual verdict"):
            build_review(self.effective, [], [])


if __name__ == "__main__":
    unittest.main()
