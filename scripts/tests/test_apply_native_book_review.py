from __future__ import annotations

import csv
import io
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

from apply_native_book_review import apply_review, json_bytes
from catalog_utils import source_hash


class ApplyNativeBookReviewTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.version_root = self.root / "versions/test"
        self.instance_root = self.root / "instance"
        self.review_dir = self.root / "reviews"
        self.version_root.mkdir(parents=True)
        self.instance_root.mkdir()
        self.review_dir.mkdir()

    def tearDown(self) -> None:
        self.temp.cleanup()

    def write_manifest(self, records: list[dict[str, object]]) -> None:
        (self.version_root / "manifest.json").write_text(
            json.dumps({"version": "test", "records": records}),
            encoding="utf-8",
        )

    def write_review(
        self,
        rows: list[dict[str, str]],
        *,
        verdict_header: str = "verdict",
        name: str = "review.tsv",
    ) -> None:
        fields = [
            "id",
            "source_hash",
            "source",
            verdict_header,
            "recommendation",
        ]
        with (self.review_dir / name).open(
            "w", encoding="utf-8", newline=""
        ) as handle:
            writer = csv.DictWriter(handle, fields, delimiter="\t")
            writer.writeheader()
            for row in rows:
                output = dict(row)
                output[verdict_header] = output.pop("verdict")
                writer.writerow(output)

    @staticmethod
    def record(
        logical_id: str,
        source: str,
        output_format: str,
        **values: object,
    ) -> dict[str, object]:
        return {
            "id": logical_id,
            "source": source,
            "source_hash": source_hash(source),
            "output_format": output_format,
            **values,
        }

    @staticmethod
    def row(
        record: dict[str, object], verdict: str, recommendation: str = ""
    ) -> dict[str, str]:
        return {
            "id": str(record["id"]),
            "source_hash": str(record["source_hash"]),
            "source": str(record["source"]),
            "verdict": verdict,
            "recommendation": recommendation,
        }

    def test_applies_only_changes_and_merges_language_overrides(self) -> None:
        changed = self.record(
            "book:changed",
            "Old English",
            "lang",
            namespace="example",
            translation_key="book.changed",
        )
        passed = self.record(
            "book:passed",
            "Already good",
            "lang",
            namespace="example",
            translation_key="book.passed",
        )
        exception = self.record(
            "book:long",
            "Long source",
            "lang",
            namespace="other",
            translation_key="book.long",
        )
        self.write_manifest([changed, passed, exception])
        self.write_review(
            [
                self.row(changed, "CHANGE", "Новый текст"),
                self.row(passed, "PASS"),
                self.row(
                    exception, "LENGTH_EXCEPTION", "Длинный вариант"
                ),
            ],
            verdict_header="verdict(PASS|CHANGE|LENGTH_EXCEPTION)",
        )
        target = (
            self.version_root
            / "translation-overrides/assets/example/lang/ru_ru.json"
        )
        target.parent.mkdir(parents=True)
        target.write_bytes(
            json_bytes(
                {
                    "book.changed": "Старый текст",
                    "unrelated.key": "Не менять",
                }
            )
        )

        counts = apply_review(
            self.version_root, self.instance_root, self.review_dir
        )

        self.assertEqual((1, 2, 1), (counts.applied, counts.skipped, counts.files))
        self.assertEqual(
            {
                "book.changed": "Новый текст",
                "unrelated.key": "Не менять",
            },
            json.loads(target.read_text(encoding="utf-8")),
        )
        self.assertFalse(
            (
                self.version_root
                / "translation-overrides/assets/other/lang/ru_ru.json"
            ).exists()
        )

    def test_builds_patchouli_override_from_nested_native_json(self) -> None:
        first = self.record(
            "patchouli:first",
            "First",
            "patchouli_json",
            location={
                "archive": "mods/outer.jar",
                "nested_archive": "META-INF/jarjar/book.jar",
                "member": "assets/example/patchouli_books/guide/en_us/page.json",
                "output_member": "assets/example/patchouli_books/guide/ru_ru/page.json",
                "pointer": "/pages/0/text",
            },
        )
        second = self.record(
            "patchouli:second",
            "Second",
            "patchouli_json",
            location={**first["location"], "pointer": "/pages/1/text"},
        )
        self.write_manifest([first, second])
        self.write_review(
            [
                self.row(first, "CHANGE", "Исправлено"),
                self.row(second, "PASS"),
            ]
        )
        native = {
            "title": "Штатный заголовок",
            "pages": [{"text": "Первый"}, {"text": "Второй"}],
        }
        nested_bytes = io.BytesIO()
        with zipfile.ZipFile(nested_bytes, "w") as nested:
            nested.writestr(first["location"]["output_member"], json_bytes(native))
        outer = self.instance_root / "mods/outer.jar"
        outer.parent.mkdir()
        with zipfile.ZipFile(outer, "w") as archive:
            archive.writestr("META-INF/jarjar/book.jar", nested_bytes.getvalue())

        counts = apply_review(
            self.version_root, self.instance_root, self.review_dir
        )

        target = (
            self.version_root
            / "translation-overrides"
            / first["location"]["output_member"]
        )
        self.assertEqual((1, 1, 1), (counts.applied, counts.skipped, counts.files))
        self.assertEqual(
            {
                "title": "Штатный заголовок",
                "pages": [{"text": "Исправлено"}, {"text": "Второй"}],
            },
            json.loads(target.read_text(encoding="utf-8")),
        )

    def test_existing_patchouli_override_is_the_base(self) -> None:
        record = self.record(
            "patchouli:title",
            "Title",
            "patchouli_json",
            location={
                "archive": "mods/missing.jar",
                "member": "assets/example/patchouli_books/guide/en_us/page.json",
                "output_member": "assets/example/patchouli_books/guide/ru_ru/page.json",
                "pointer": "/title",
            },
        )
        self.write_manifest([record])
        self.write_review([self.row(record, "CHANGE", "Новый заголовок")])
        target = (
            self.version_root
            / "translation-overrides"
            / record["location"]["output_member"]
        )
        target.parent.mkdir(parents=True)
        target.write_bytes(
            json_bytes({"title": "Старый", "unrelated": {"value": 7}})
        )

        apply_review(self.version_root, self.instance_root, self.review_dir)

        self.assertEqual(
            {"title": "Новый заголовок", "unrelated": {"value": 7}},
            json.loads(target.read_text(encoding="utf-8")),
        )

    def test_existing_payload_preserves_generated_patchouli_fields(self) -> None:
        record = self.record(
            "patchouli:title",
            "Title",
            "patchouli_json",
            location={
                "archive": "mods/missing.jar",
                "member": "assets/example/patchouli_books/guide/en_us/page.json",
                "output_member": "assets/example/patchouli_books/guide/ru_ru/page.json",
                "pointer": "/title",
            },
        )
        self.write_manifest([record])
        self.write_review([self.row(record, "CHANGE", "Новый заголовок")])
        relative = Path(record["location"]["output_member"])
        built = self.version_root / "payload/resourcepack" / relative
        built.parent.mkdir(parents=True)
        built.write_bytes(
            json_bytes(
                {
                    "title": "Старый заголовок",
                    "generated_translation": "Сохранить",
                }
            )
        )

        apply_review(self.version_root, self.instance_root, self.review_dir)

        target = self.version_root / "translation-overrides" / relative
        self.assertEqual(
            {
                "title": "Новый заголовок",
                "generated_translation": "Сохранить",
            },
            json.loads(target.read_text(encoding="utf-8")),
        )

    def test_check_requires_byte_equal_output_and_does_not_write(self) -> None:
        record = self.record(
            "book:key",
            "Source",
            "lang",
            namespace="example",
            translation_key="book.key",
        )
        self.write_manifest([record])
        self.write_review([self.row(record, "CHANGE", "Перевод")])
        target = (
            self.version_root
            / "translation-overrides/assets/example/lang/ru_ru.json"
        )

        with self.assertRaisesRegex(ValueError, "override is not current"):
            apply_review(
                self.version_root,
                self.instance_root,
                self.review_dir,
                check=True,
            )
        self.assertFalse(target.exists())

        apply_review(self.version_root, self.instance_root, self.review_dir)
        before = target.read_bytes()
        counts = apply_review(
            self.version_root,
            self.instance_root,
            self.review_dir,
            check=True,
        )
        self.assertEqual(before, target.read_bytes())
        self.assertEqual(1, counts.files)

    def test_rejects_invalid_review_rows_before_writing(self) -> None:
        record = self.record(
            "manual:text",
            "Source",
            "manual_text",
            location={"output_member": "assets/example/manual/ru_ru/page.txt"},
        )
        self.write_manifest([record])

        cases = [
            (
                {**self.row(record, "CHANGE", "Перевод"), "source": "Changed"},
                "source differs",
            ),
            (
                {
                    **self.row(record, "CHANGE", "Перевод"),
                    "source_hash": "sha256:stale",
                },
                "source hash differs",
            ),
            (self.row(record, "CHANGE"), "requires a recommendation"),
            (
                self.row(record, "LENGTH_EXCEPTION"),
                "LENGTH_EXCEPTION requires a recommendation",
            ),
            (self.row(record, "CHANGE", "Перевод"), "unsupported output format"),
        ]
        for row, error in cases:
            with self.subTest(error=error):
                for path in self.review_dir.glob("*.tsv"):
                    path.unlink()
                self.write_review([row])
                with self.assertRaisesRegex(ValueError, error):
                    apply_review(
                        self.version_root, self.instance_root, self.review_dir
                    )
        self.assertFalse((self.version_root / "translation-overrides").exists())


if __name__ == "__main__":
    unittest.main()
