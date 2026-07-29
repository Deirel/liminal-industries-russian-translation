import sys
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from approve_version_translations import TOKEN_RE


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


if __name__ == "__main__":
    unittest.main()
