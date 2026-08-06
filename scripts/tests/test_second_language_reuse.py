import sys
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from build_second_language_reuse import apply_review_fix, build_reuse, combine_consensus


def corpus(logical_id: str, context: str = "context") -> dict[str, str]:
    return {
        "id": logical_id,
        "source_hash": "hash",
        "source": "Source",
        "effective_translation": "Перевод",
        "context": context,
        "audit_class": "PROSE",
    }


def review(logical_id: str, reviewer: str, verdict: str) -> dict[str, str]:
    return {"id": logical_id, "verdict": verdict, "recommendation": "", "reviewer": reviewer}


def consensus(logical_id: str, adjudicator: str = "") -> dict[str, str]:
    return {
        "id": logical_id,
        "verdict": "PASS",
        "recommendation": "",
        "adjudicator": adjudicator,
    }


class SecondLanguageReuseTest(unittest.TestCase):
    def test_reuses_only_exact_unadjudicated_agreement(self) -> None:
        source = [corpus("exact"), corpus("disputed"), corpus("different")]
        first = [review(row["id"], "a", "PASS") for row in source]
        second = [review(row["id"], "b", "PASS") for row in source]
        source_consensus = [consensus("exact"), consensus("disputed", "judge"), consensus("different")]
        target = [corpus("exact"), corpus("disputed"), corpus("different", "changed")]
        reused, pending = build_reuse(source, first, second, source_consensus, target)
        self.assertEqual(["exact"], [row["id"] for row in reused])
        self.assertEqual(["disputed", "different"], [row["id"] for row in pending])

    def test_combines_reused_and_original_decisions_in_target_order(self) -> None:
        target = [corpus("first"), corpus("second")]
        reused = [{**consensus("second"), "source_hash": "hash"}]
        reviewed = [{**consensus("first"), "source_hash": "hash"}]
        full = combine_consensus(target, reused, reviewed)
        self.assertEqual(["first", "second"], [row["id"] for row in full])

    def test_applies_review_fix_and_allows_terminal_style_reset(self) -> None:
        source = [{**corpus("fixed"), "source": "$(o)Source", "effective_translation": "$(o)Старый"}]
        base = [{**consensus("fixed"), "source_hash": "hash", "reason": "", "reviewer_a": "a", "reviewer_b": "b", "review_notes": ""}]
        findings = [{"id": "fixed"}]
        proposals = [{
            "id": "fixed",
            "source_hash": "hash",
            "verdict": "CHANGE",
            "recommendation": "$(o)Новый$(0)",
            "reason": "Исправлено.",
            "reviewer": "fixer",
            "method": "review-fix",
        }]
        fixed = apply_review_fix(source, base, findings, proposals)
        self.assertEqual("CHANGE", fixed[0]["verdict"])
        self.assertEqual("$(o)Новый$(0)", fixed[0]["recommendation"])
        self.assertEqual("CHECKPOINT_REVIEW_FIX", fixed[0]["review_notes"])


if __name__ == "__main__":
    unittest.main()
