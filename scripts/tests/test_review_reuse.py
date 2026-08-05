import sys
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from build_review_reuse import exact_match


class ReviewReuseTest(unittest.TestCase):
    def test_requires_exact_id_source_translation_and_context(self) -> None:
        row = {
            "id": "item:item.test.widget",
            "source_hash": "hash",
            "source": "Widget",
            "effective_translation": "Виджет",
            "context": '{"chapter":"one"}',
        }
        self.assertTrue(exact_match(row, dict(row)))
        for field in row:
            with self.subTest(field=field):
                changed = dict(row)
                changed[field] += "-changed"
                self.assertFalse(exact_match(row, changed))


if __name__ == "__main__":
    unittest.main()
