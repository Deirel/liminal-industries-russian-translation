import sys
import json
import tempfile
import unittest
import hashlib
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from merge_second_language_reviews import (
    build_consensus,
    validate_pair_provenance,
    validate_summary,
)


class MergeSecondLanguageReviewsTest(unittest.TestCase):
    def setUp(self) -> None:
        self.corpus = [
            {
                "id": "patchouli:test",
                "source_hash": "hash",
                "source": "$(o)Read this.",
                "effective_translation": "$(o)Прочтите [$(k:use)] это.",
                "plain_source": "Read this.",
                "plain_translation": "Прочтите это.",
                "context": "{}",
                "audit_class": "PROSE",
                "group_key": "book:test",
            }
        ]

    def review(self, reviewer: str, verdict: str, recommendation: str = "", reason: str = "") -> dict[str, str]:
        return {
            "id": "patchouli:test",
            "source_hash": "hash",
            "verdict": verdict,
            "recommendation": recommendation,
            "reason": reason,
            "checked_dimensions": "PUNCT|GRAMMAR|SYNTAX|GOVERNMENT|COLLOCATION|COHERENCE|CALQUE|WORD_ORDER|NATURALNESS|TERMINOLOGY|REGISTER",
            "reviewer": reviewer,
            "method": "blind",
        }

    def test_agreed_pass_is_complete(self) -> None:
        rows, disagreements = build_consensus(
            self.corpus, [self.review("a", "PASS")], [self.review("b", "PASS")]
        )
        self.assertEqual(("PASS", 0), (rows[0]["verdict"], len(disagreements)))

    def test_disagreement_requires_exact_adjudication(self) -> None:
        first = self.review(
            "a", "CHANGE", "$(o)Прочитайте [$(k:use)] это.", "Естественнее"
        )
        second = self.review("b", "PASS")
        rows, disagreements = build_consensus(self.corpus, [first], [second])
        self.assertEqual((0, 1), (len(rows), len(disagreements)))
        adjudication = [
            {
                "id": "patchouli:test",
                "source_hash": "hash",
                "verdict": "CHANGE",
                "recommendation": "$(o)Прочитайте [$(k:use)] это.",
                "reason": "Естественнее",
                "adjudicator": "c",
                "notes": "Выбран естественный вариант.",
            }
        ]
        rows, _ = build_consensus(self.corpus, [first], [second], adjudication)
        self.assertEqual("c", rows[0]["adjudicator"])

    def test_rejects_incomplete_review(self) -> None:
        with self.assertRaisesRegex(ValueError, "expected 1 rows"):
            build_consensus(self.corpus, [], [self.review("b", "PASS")])

    def test_validates_blind_terra_medium_summary(self) -> None:
        review = [self.review("a", "PASS")]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            corpus = root / "corpus.tsv"
            corpus.write_text("corpus\n", encoding="utf-8")
            summary = root / "summary.json"
            summary.write_text(
                json.dumps(
                    {
                        "input_sha256": hashlib.sha256(corpus.read_bytes()).hexdigest(),
                        "input_rows": 1,
                        "output_rows": 1,
                        "counts": {"PASS": 1, "CHANGE": 0, "UNCERTAIN": 0},
                        "reviewer": "a",
                        "model": "gpt-5.6-terra",
                        "reasoning": "medium",
                        "blind": True,
                    }
                ),
                encoding="utf-8",
            )
            validate_summary(summary, corpus, review, "review-a")

    def test_requires_distinct_blind_reviewers_and_methods(self) -> None:
        first = [self.review("a", "PASS")]
        second = [{**self.review("b", "PASS"), "method": "blind-b"}]
        validate_pair_provenance(first, second, "a", "b")
        with self.assertRaisesRegex(ValueError, "distinct reviewers"):
            validate_pair_provenance(first, first, "a", "a")


if __name__ == "__main__":
    unittest.main()
