import io
import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from translation_sources import (
    SourceDefinition,
    SourceResult,
    collect_immersive_engineering_manual,
    collect_mantle_books,
    collect_patchouli,
    collect_sources,
    load_source_definitions,
    parse_mantle_language,
)
from build_version_delta import (
    annotate_translation_state,
    classify_translation_record,
    review_identity,
)
from build_version_resources import (
    build_manual_files,
    build_mantle_book_files,
    build_mantle_book_language_files,
    build_patchouli_files,
)
from catalog_utils import sha256_bytes, source_hash


class SourceConfigurationTest(unittest.TestCase):
    def test_loads_ordered_source_definitions(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "sources.json"
            path.write_text(
                json.dumps(
                    {
                        "schema": 1,
                        "sources": [
                            {"id": "items", "adapter": "registry_items"},
                            {
                                "id": "patchouli",
                                "adapter": "patchouli",
                                "audit": True,
                                "tier": "extended",
                            },
                        ],
                    }
                ),
                encoding="utf-8",
            )

            definitions = load_source_definitions(path)

        self.assertEqual(["items", "patchouli"], [value.source_id for value in definitions])
        self.assertEqual({"audit": True}, definitions[1].options)
        self.assertEqual("required", definitions[0].tier)
        self.assertEqual("extended", definitions[1].tier)

    def test_rejects_unknown_translation_tier(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "sources.json"
            path.write_text(
                json.dumps(
                    {
                        "schema": 1,
                        "sources": [
                            {
                                "id": "example",
                                "adapter": "example",
                                "tier": "optional",
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(ValueError, "invalid tier"):
                load_source_definitions(path)

    def test_extended_source_stamps_manifest_records(self) -> None:
        result = collect_sources(
            [SourceDefinition("example", "example", {}, "extended")],
            {
                "example": lambda definition: SourceResult(
                    records=[{"id": "example:key"}]
                )
            },
        )

        self.assertEqual("extended", result.records[0]["tier"])


class PatchouliSourceTest(unittest.TestCase):
    def test_collects_language_keys_and_literal_json_fields(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            instance = Path(directory)
            mods = instance / "mods"
            mods.mkdir()
            archive = mods / "guide.jar"
            with zipfile.ZipFile(archive, "w") as jar:
                jar.writestr(
                    "assets/example/patchouli_books/guide/en_us/categories/main.json",
                    json.dumps(
                        {
                            "name": "guide.category.main",
                            "description": "Literal description",
                            "icon": "minecraft:book",
                        }
                    ),
                )
                jar.writestr(
                    "data/example/patchouli_books/guide/book.json",
                    json.dumps(
                        {
                            "name": "guide.name",
                            "landing_text": "Literal landing text",
                        }
                    ),
                )

            result = collect_patchouli(
                SourceDefinition("patchouli", "patchouli", {}),
                [archive],
                instance,
                {
                    "guide.category.main": "Main category",
                    "guide.name": "Guide",
                },
                {},
            )

        by_kind = {}
        for record in result.records:
            by_kind.setdefault(record["kind"], []).append(record)
        self.assertEqual(2, len(by_kind["patchouli_book_metadata"]))
        self.assertEqual(1, len(by_kind["patchouli_language_text"]))
        self.assertEqual(1, len(by_kind["patchouli_json_text"]))
        direct = by_kind["patchouli_json_text"][0]
        self.assertEqual("Literal description", direct["source"])
        self.assertEqual(
            "assets/example/patchouli_books/guide/ru_ru/categories/main.json",
            direct["location"]["output_member"],
        )

    def test_collects_and_builds_patchouli_from_nested_jar(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            instance = Path(directory)
            mods = instance / "mods"
            mods.mkdir()
            archive = mods / "outer.jar"
            nested_member = "META-INF/jarjar/guide-core.jar"
            member = (
                "assets/example/patchouli_books/guide/en_us/entries/start.json"
            )
            source = json.dumps({"text": "Nested welcome"}).encode()
            native_member = member.replace("/en_us/", "/ru_ru/")
            source = json.dumps(
                {"name": "Start", "text": "Nested welcome"}
            ).encode()
            nested_bytes = io.BytesIO()
            with zipfile.ZipFile(nested_bytes, "w") as nested:
                nested.writestr(member, source)
                nested.writestr(
                    native_member,
                    json.dumps({"name": "Начало"}),
                )
            with zipfile.ZipFile(archive, "w") as jar:
                jar.writestr(nested_member, nested_bytes.getvalue())

            result = collect_patchouli(
                SourceDefinition("patchouli", "patchouli", {}),
                [archive],
                instance,
                {},
                {},
            )
            record = next(
                value
                for value in result.records
                if value["kind"] == "patchouli_json_text"
                and not value["native_ru_present"]
            )
            manifest = {
                "records": [record],
                "source_files": result.source_files,
            }
            catalog = {
                "entries": {
                    record["id"]: [
                        {
                            "source": record["source"],
                            "source_hash": record["source_hash"],
                            "translation": "Вложенное приветствие",
                        }
                    ]
                }
            }
            output = instance / "output"

            count = build_patchouli_files(
                manifest,
                catalog,
                instance,
                output,
            )
            built = json.loads(
                (
                    output
                    / "assets/example/patchouli_books/guide/ru_ru/entries/start.json"
                ).read_text(encoding="utf-8")
            )

        self.assertEqual(nested_member, record["location"]["nested_archive"])
        self.assertEqual(1, count)
        self.assertEqual("Начало", built["name"])
        self.assertEqual("Вложенное приветствие", built["text"])

    def test_builds_localized_patchouli_json(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            instance = Path(directory)
            mods = instance / "mods"
            mods.mkdir()
            archive = mods / "guide.jar"
            member = (
                "assets/example/patchouli_books/guide/en_us/entries/start.json"
            )
            source = json.dumps(
                {
                    "name": "Start",
                    "pages": [{"type": "patchouli:text", "text": "Welcome"}],
                }
            ).encode()
            with zipfile.ZipFile(archive, "w") as jar:
                jar.writestr(member, source)
            record = {
                "id": "patchouli-json:example:guide:entries/start.json:/pages/0/text",
                "kind": "patchouli_json_text",
                "source": "Welcome",
                "source_hash": "welcome-hash",
                "native_ru_present": False,
                "output_format": "patchouli_json",
                "location": {
                    "archive": "mods/guide.jar",
                    "member": member,
                    "output_member": member.replace("/en_us/", "/ru_ru/"),
                    "pointer": "/pages/0/text",
                },
            }
            manifest = {
                "records": [record],
                "source_files": [
                    {
                        "path": f"mods/guide.jar!/{member}",
                        "sha256": sha256_bytes(source),
                    }
                ],
            }
            catalog = {
                "entries": {
                    record["id"]: [
                        {
                            "source": "Welcome",
                            "source_hash": "welcome-hash",
                            "translation": "Добро пожаловать",
                        }
                    ]
                }
            }
            output = instance / "output"

            count = build_patchouli_files(
                manifest,
                catalog,
                instance,
                output,
            )
            built = json.loads(
                (
                    output
                    / "assets/example/patchouli_books/guide/ru_ru/entries/start.json"
                ).read_text(encoding="utf-8")
            )

        self.assertEqual(1, count)
        self.assertEqual("Добро пожаловать", built["pages"][0]["text"])
        self.assertEqual("Start", built["name"])


class ReviewedNativeBookSourceTest(unittest.TestCase):
    def test_collects_and_builds_missing_mantle_language_entries(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            instance = Path(directory)
            mods = instance / "mods"
            mods.mkdir()
            archive = mods / "tconstruct.jar"
            en_member = (
                "assets/tconstruct/book/guide/en_us/language.lang"
            )
            ru_member = en_member.replace("/en_us/", "/ru_ru/")
            english = (
                "intro=Introduction\n"
                "materials=General Materials\n"
                "materials.subtext=Material details\n"
            ).encode()
            russian = (
                "intro=Вступление\n"
                "legacy=Сохранённая подпись\n"
            ).encode()
            with zipfile.ZipFile(archive, "w") as jar:
                jar.writestr(en_member, english)
                jar.writestr(ru_member, russian)

            result = collect_mantle_books(
                SourceDefinition(
                    "books",
                    "mantle_books",
                    {"namespace": "tconstruct", "review_native": False},
                ),
                [archive],
                instance,
            )
            records = {
                record["location"]["key"]: record
                for record in result.records
            }
            self.assertEqual(
                {"intro", "materials", "materials.subtext"},
                set(records),
            )
            self.assertTrue(records["intro"]["native_ru_present"])
            self.assertFalse(records["intro"]["force_output"])
            self.assertFalse(records["materials"]["native_ru_present"])
            self.assertTrue(records["materials"]["force_output"])
            self.assertEqual(
                2, result.report["missing_native_language_entries"]
            )

            missing = [
                record
                for record in result.records
                if record["force_output"]
            ]
            catalog = {
                "entries": {
                    record["id"]: [
                        {
                            "source": record["source"],
                            "source_hash": record["source_hash"],
                            "translation": f"Перевод {index}",
                        }
                    ]
                    for index, record in enumerate(missing)
                }
            }
            output = instance / "output"
            count = build_mantle_book_language_files(
                {
                    "records": result.records,
                    "source_files": result.source_files,
                },
                catalog,
                instance,
                output,
            )
            built = parse_mantle_language(
                (output / ru_member).read_bytes(), ru_member
            )

        self.assertEqual(2, count)
        self.assertEqual("Вступление", built["intro"])
        self.assertEqual("Сохранённая подпись", built["legacy"])
        self.assertEqual("Перевод 0", built["materials"])
        self.assertEqual("Перевод 1", built["materials.subtext"])

    def test_finalized_catalog_entry_wins_over_native_review(self) -> None:
        record = {
            "id": "manual:test:intro:line:0",
            "source": "Current English",
            "source_hash": source_hash("Current English"),
            "native_ru_present": True,
            "native_translation": "Штатный перевод",
            "native_translation_status": "REVIEW_NATIVE",
            "review_native": True,
        }
        catalog = {
            "entries": {
                record["id"]: [
                    {
                        "source": record["source"],
                        "source_hash": record["source_hash"],
                        "translation": "Утверждённый перевод",
                    }
                ]
            }
        }

        self.assertEqual(
            "FINALIZED", classify_translation_record(catalog, record)
        )
        record["native_translation"] = "Изменённый штатный перевод"
        self.assertEqual(
            "FINALIZED", classify_translation_record(catalog, record)
        )
        changed = {
            **record,
            "source": "Changed English",
            "source_hash": source_hash("Changed English"),
        }
        self.assertEqual(
            "REVIEW_NATIVE", classify_translation_record(catalog, changed)
        )

    def test_update_reuses_only_exact_approved_effective_records(self) -> None:
        current = {
            "id": "item:item.test.kept",
            "kind": "item_name",
            "source_id": "items",
            "source": "Kept",
            "source_hash": source_hash("Kept"),
            "native_ru_present": True,
            "native_translation": "Сохранено",
            "namespace": "test",
            "translation_key": "item.test.kept",
            "output_format": "lang",
        }
        previous = {
            **current,
            "effective_translation": "Сохранено",
            "effective_translation_origin": "native_ru",
            "effective_translation_approved": True,
        }
        deleted = {
            **previous,
            "id": "item:item.test.deleted",
            "translation_key": "item.test.deleted",
        }
        approved = {
            review_identity(previous): [previous],
            review_identity(deleted): [deleted],
        }
        changed = {
            **current,
            "source": "Changed",
            "source_hash": source_hash("Changed"),
        }
        new = {
            **current,
            "id": "item:item.test.new",
            "translation_key": "item.test.new",
        }
        catalog = {"entries": {}}
        with tempfile.TemporaryDirectory() as directory:
            version_root = Path(directory)
            kept_status = annotate_translation_state(
                current, catalog, approved, version_root
            )
            changed_status = annotate_translation_state(
                changed, catalog, approved, version_root
            )
            new_status = annotate_translation_state(
                new, catalog, approved, version_root
            )

        self.assertEqual("FINALIZED", kept_status)
        self.assertEqual("REVIEW_NATIVE", changed_status)
        self.assertEqual("REVIEW_NATIVE", new_status)
        self.assertNotIn(deleted["id"], {current["id"], new["id"]})

        suppressed = {
            "id": "patchouli:test:entry:/text",
            "kind": "patchouli_text",
            "source_id": "patchouli",
            "source": "Text",
            "source_hash": source_hash("Text"),
            "output_format": "patchouli_json",
            "location": {
                "output_member": "assets/test/book/ru_ru/entry.json",
                "pointer": "/text",
            },
        }
        previously_visible = {
            **suppressed,
            "effective_translation": "",
            "effective_translation_origin": "translation-overrides",
            "effective_translation_approved": True,
        }
        approved_visible = {
            review_identity(previously_visible): [previously_visible]
        }
        with tempfile.TemporaryDirectory() as directory:
            version_root = Path(directory)
            override = (
                version_root
                / "translation-overrides/assets/test/book/ru_ru/entry.json"
            )
            override.parent.mkdir(parents=True)
            override.write_text("{}", encoding="utf-8")
            suppressed_status = annotate_translation_state(
                suppressed, catalog, approved_visible, version_root
            )
        self.assertEqual("PENDING", suppressed_status)

    def test_collects_and_rebuilds_ie_manual_from_english_structure(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            instance = Path(directory)
            mods = instance / "mods"
            mods.mkdir()
            archive = mods / "ie.jar"
            en_member = (
                "assets/immersiveengineering/manual/en_us/example.txt"
            )
            ru_member = en_member.replace("/en_us/", "/ru_ru/")
            with zipfile.ZipFile(archive, "w") as jar:
                jar.writestr(
                    en_member,
                    "Title\n<np>\n<&image>English text\nNew line",
                )
                jar.writestr(ru_member, "Заголовок\n<&image>Старый текст")

            result = collect_immersive_engineering_manual(
                SourceDefinition(
                    "manual",
                    "immersive_engineering_manual",
                    {
                        "namespace": "immersiveengineering",
                        "review_native": True,
                    },
                ),
                [archive],
                instance,
            )
            self.assertEqual(
                {"STALE_NATIVE"},
                {
                    record["native_translation_status"]
                    for record in result.records
                },
            )
            self.assertEqual(3, len(result.records))
            self.assertNotIn(
                "<np>",
                {record["source"] for record in result.records},
            )
            self.assertTrue(
                all(
                    "native_translation" not in record
                    and "suggested_translation" not in record
                    for record in result.records
                )
            )
            catalog = {
                "entries": {
                    record["id"]: [
                        {
                            "source": record["source"],
                            "source_hash": record["source_hash"],
                            "translation": f"Перевод {index}",
                        }
                    ]
                    for index, record in enumerate(result.records)
                }
            }
            output = instance / "output"
            count = build_manual_files(
                {
                    "records": result.records,
                    "source_files": result.source_files,
                },
                catalog,
                instance,
                output,
            )

            built = (output / ru_member).read_text(encoding="utf-8")
        self.assertEqual(3, count)
        self.assertEqual(
            "Перевод 0\n<np>\nПеревод 1\nПеревод 2",
            built,
        )

    def test_collects_ie_manual_navigation_language_keys(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            instance = Path(directory)
            mods = instance / "mods"
            mods.mkdir()
            archive = mods / "ie.jar"
            with zipfile.ZipFile(archive, "w") as jar:
                jar.writestr(
                    "assets/immersiveengineering/manual/en_us/example.txt",
                    "Title",
                )

            result = collect_immersive_engineering_manual(
                SourceDefinition(
                    "manual",
                    "immersive_engineering_manual",
                    {"namespace": "immersiveengineering"},
                ),
                [archive],
                instance,
                {
                    "manual.immersiveengineering.general": "General",
                    "manual.immersiveengineering.early_machines": (
                        "Workbenches & Furnaces"
                    ),
                    "item.immersiveengineering.manual": "Engineer's Manual",
                },
                {
                    "manual.immersiveengineering.general": "Общие сведения",
                },
                {},
            )

        language_records = [
            record
            for record in result.records
            if record["output_format"] == "lang"
        ]
        self.assertEqual(2, len(language_records))
        by_key = {
            record["translation_key"]: record
            for record in language_records
        }
        self.assertTrue(
            by_key["manual.immersiveengineering.general"][
                "native_ru_present"
            ]
        )
        missing = by_key[
            "manual.immersiveengineering.early_machines"
        ]
        self.assertFalse(missing["native_ru_present"])
        self.assertEqual(
            "Workbenches & Furnaces",
            missing["source"],
        )

    def test_mantle_structure_is_classified_before_records(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            instance = Path(directory)
            mods = instance / "mods"
            mods.mkdir()
            archive = mods / "tconstruct.jar"
            en_member = "assets/tconstruct/book/guide/en_us/page.json"
            ru_member = en_member.replace("/en_us/", "/ru_ru/")
            english = {
                "title": "Page",
                "text": [
                    {"text": "Description"},
                    {"text": " \n "},
                ],
                "tool_filter": "tconstruct:modifiable",
            }
            russian = {
                "title": "Страница",
                "text": [
                    {"text": "Описание"},
                    {"text": " \n "},
                ],
                "tool_filter": "tconstruct:wrong",
            }
            with zipfile.ZipFile(archive, "w") as jar:
                jar.writestr(en_member, json.dumps(english))
                jar.writestr(ru_member, json.dumps(russian))

            result = collect_mantle_books(
                SourceDefinition(
                    "books",
                    "mantle_books",
                    {"namespace": "tconstruct", "review_native": True},
                ),
                [archive],
                instance,
            )

        self.assertEqual(2, len(result.records))
        self.assertEqual(
            {"STALE_NATIVE"},
            {
                record["native_translation_status"]
                for record in result.records
            },
        )
        self.assertTrue(
            all(
                not record["native_ru_present"]
                and "native_translation" not in record
                and "suggested_translation" not in record
                for record in result.records
            )
        )
        self.assertEqual(1, result.report["stale_native_pages"])

    def test_invalid_mantle_translation_enters_delta_and_rebuilds(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            instance = Path(directory)
            mods = instance / "mods"
            mods.mkdir()
            archive = mods / "tconstruct.jar"
            en_member = (
                "assets/tconstruct/book/guide/en_us/page.json"
            )
            ru_member = en_member.replace("/en_us/", "/ru_ru/")
            english = {
                "title": "Page",
                "text": [{"text": "Description"}],
                "effects": ["First effect", "Second effect"],
                "modifier_id": "tconstruct:test",
            }
            with zipfile.ZipFile(archive, "w") as jar:
                jar.writestr(en_member, json.dumps(english))
                jar.writestr(ru_member, '{"title":"Страница" "text":[]}')

            result = collect_mantle_books(
                SourceDefinition(
                    "books",
                    "mantle_books",
                    {"namespace": "tconstruct", "review_native": True},
                ),
                [archive],
                instance,
            )
            self.assertEqual(
                {"INVALID_NATIVE"},
                {
                    record["native_translation_status"]
                    for record in result.records
                },
            )
            catalog = {
                "entries": {
                    record["id"]: [
                        {
                            "source": record["source"],
                            "source_hash": record["source_hash"],
                            "translation": f"Перевод {index}",
                        }
                    ]
                    for index, record in enumerate(result.records)
                }
            }
            output = instance / "output"
            count = build_mantle_book_files(
                {
                    "records": result.records,
                    "source_files": result.source_files,
                },
                catalog,
                instance,
                output,
            )
            built = json.loads((output / ru_member).read_text(encoding="utf-8"))

        self.assertEqual(4, count)
        self.assertEqual("tconstruct:test", built["modifier_id"])
        self.assertEqual("Перевод 2", built["effects"][0])
        self.assertEqual("Перевод 0", built["title"])


if __name__ == "__main__":
    unittest.main()
