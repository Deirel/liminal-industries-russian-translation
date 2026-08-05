import sys
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from build_second_language_corpus import build_corpus, plain_text


class SecondLanguageCorpusTest(unittest.TestCase):
    def test_groups_visible_prose_without_prior_verdicts(self) -> None:
        rows = build_corpus(
            [
                {
                    "id": "patchouli-lang:botania:botania.page.welcome0",
                    "source_hash": "hash",
                    "source": "$(o)Use$(p)$(li)Arrows",
                    "effective_translation": "$(o)Листайте$(p)$(li)Стрелки",
                    "context": '{"kind":"patchouli_language_text","location":{"output_member":"assets/botania/patchouli_books/lexicon/ru_ru/entries/basics/welcome.json"},"source_id":"patchouli","state":"visible"}',
                    "verdict": "PASS",
                },
                {
                    "id": "item:item.test.hidden",
                    "source_hash": "hash2",
                    "source": "Hidden",
                    "effective_translation": "",
                    "context": '{"kind":"item_name","location":{},"source_id":"items","state":"suppressed_by_override"}',
                },
            ]
        )
        self.assertEqual(1, len(rows))
        self.assertEqual("PROSE", rows[0]["audit_class"])
        self.assertNotIn("verdict", rows[0])
        self.assertIn("/<language>/", rows[0]["group_key"])

    def test_plain_text_keeps_link_label_and_structure(self) -> None:
        self.assertEqual(
            "Текст\n\n- Провод\n[keybind:key.jump]",
            plain_text(
                "$(o)Текст$(p)$(li)<link;wiring;Провод;page>$(br2)$(k:key.jump)"
            ),
        )

    def test_keeps_each_member_together_and_orders_lines_naturally(self) -> None:
        def row(member: str, line: int) -> dict[str, str]:
            return {
                "id": f"ie-manual:{member}:line:{line}",
                "source_hash": f"hash-{member}-{line}",
                "source": f"{member}:{line}",
                "effective_translation": f"{member}:{line}",
                "context": '{"kind":"manual_text","location":{"line":%d,"member":"assets/test/manual/ru_ru/%s"},"source_id":"immersive_engineering","state":"visible"}'
                % (line, member),
            }

        rows = build_corpus(
            [row("second.txt", 1), row("first.txt", 10), row("first.txt", 2), row("first.txt", 1)]
        )
        self.assertEqual(
            ["first.txt:1", "first.txt:2", "first.txt:10", "second.txt:1"],
            [item["source"] for item in rows],
        )
        self.assertEqual(2, len({item["group_key"] for item in rows}))

    def test_treats_patchouli_metadata_as_prose(self) -> None:
        rows = build_corpus(
            [
                {
                    "id": "patchouli-lang:test:landing",
                    "source_hash": "hash",
                    "source": "Two sentences. Read them.",
                    "effective_translation": "Два предложения. Прочитайте их.",
                    "context": '{"kind":"patchouli_book_metadata","location":{"member":"assets/test/book/en_us/book.json"},"source_id":"patchouli","state":"visible"}',
                }
            ]
        )
        self.assertEqual("PROSE", rows[0]["audit_class"])


if __name__ == "__main__":
    unittest.main()
