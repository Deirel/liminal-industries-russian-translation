import io
import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from build_version_resources import (
    apply_compatibility_translations,
    apply_translation_overrides,
    build_language_files,
)
from build_version_delta import load_kubejs_registry
from item_sources import (
    load_blockstate_ids,
    load_jar_languages,
    same_source_aliases,
)
from runtime_audit_overrides import load_runtime_audit_overrides


class BlockRegistryDiscoveryTest(unittest.TestCase):
    def test_discovers_blockstate_without_language_key(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            archive = root / "example.jar"
            with zipfile.ZipFile(archive, "w") as jar:
                jar.writestr(
                    "assets/example/blockstates/hidden_machine.json",
                    '{"variants": {}}',
                )

            block_ids = load_blockstate_ids([archive], root / "kubejs")

        self.assertEqual({"example:hidden_machine"}, block_ids)


class KubeJsRegistryDiscoveryTest(unittest.TestCase):
    def test_discovers_contentpack_names_and_fluid_bucket_name(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            script = (
                root
                / "contentpacks/example/startup_scripts/registry.js"
            )
            script.parent.mkdir(parents=True)
            script.write_text(
                """
StartupEvents.registry('block', event => {
    let wallpapers = (id) => {
        event.create(id).displayName("Wallpaper")
    }
    wallpapers("wallpaper1_red")
})
StartupEvents.registry('fluid', event => {
    event.create('liquid_time').displayName('The Flow of Time')
})
""",
                encoding="utf-8",
            )

            registry = load_kubejs_registry(root)

        self.assertEqual(
            ("block.kubejs.wallpaper1_red", "Wallpaper"),
            registry["kubejs:wallpaper1_red"],
        )
        self.assertEqual(
            ("item.kubejs.liquid_time_bucket", "The Flow of Time Bucket"),
            registry["kubejs:liquid_time_bucket"],
        )


class NestedLanguageDiscoveryTest(unittest.TestCase):
    def test_loads_languages_from_nested_jar(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            mods = Path(directory)
            archive = mods / "outer.jar"
            nested_bytes = io.BytesIO()
            with zipfile.ZipFile(nested_bytes, "w") as nested:
                nested.writestr(
                    "assets/example/lang/en_us.json",
                    json.dumps(
                        {
                            "item.example.machine": "Machine",
                            "item.example.shared": "Core name",
                        }
                    ),
                )
                nested.writestr(
                    "assets/example/lang/ru_ru.json",
                    json.dumps({"item.example.machine": "Машина"}),
                )
            with zipfile.ZipFile(archive, "w") as jar:
                jar.writestr(
                    "META-INF/jarjar/example-core.jar",
                    nested_bytes.getvalue(),
                )
                jar.writestr(
                    "assets/example/lang/en_us.json",
                    json.dumps({"item.example.shared": "Outer name"}),
                )

            english, russian = load_jar_languages(mods)

        self.assertEqual("Machine", english["item.example.machine"])
        self.assertEqual("Outer name", english["item.example.shared"])
        self.assertEqual("Машина", russian["item.example.machine"])


class SameSourceAliasesTest(unittest.TestCase):
    def test_keeps_matching_block_key(self) -> None:
        aliases = same_source_aliases(
            "example:machine",
            "item.example.machine",
            "Machine",
            {
                "item.example.machine": "Machine",
                "block.example.machine": "Machine",
            },
        )

        self.assertEqual(["block.example.machine"], aliases)

    def test_rejects_key_with_different_source(self) -> None:
        aliases = same_source_aliases(
            "example:machine",
            "item.example.machine",
            "Machine",
            {
                "item.example.machine": "Machine",
                "block.example.machine": "Machine Block",
            },
        )

        self.assertEqual([], aliases)


class BuildItemAliasesTest(unittest.TestCase):
    def test_writes_primary_key_and_alias(self) -> None:
        record = {
            "id": "item:item.example.machine",
            "kind": "item_name",
            "source": "Machine",
            "source_hash": "hash",
            "namespace": "example",
            "translation_key": "item.example.machine",
            "translation_aliases": ["block.example.machine"],
            "native_ru_present": False,
            "output_format": "lang",
        }
        catalog = {
            "entries": {
                record["id"]: [
                    {
                        "source": record["source"],
                        "source_hash": record["source_hash"],
                        "translation": "Машина",
                    }
                ]
            }
        }

        with tempfile.TemporaryDirectory() as directory:
            count = build_language_files(
                {"records": [record]},
                catalog,
                {},
                Path(directory),
            )
            output = json.loads(
                (
                    Path(directory)
                    / "assets/example/lang/ru_ru.json"
                ).read_text(encoding="utf-8")
            )

        self.assertEqual(2, count)
        self.assertEqual("Машина", output["item.example.machine"])
        self.assertEqual("Машина", output["block.example.machine"])

    def test_catalog_translation_replaces_english_native_value(self) -> None:
        record = {
            "id": "item:item.example.machine",
            "source": "Machine",
            "source_hash": "hash",
            "namespace": "example",
            "translation_key": "item.example.machine",
            "native_ru_present": True,
            "native_ru_same_as_source": True,
            "output_format": "lang",
        }
        catalog = {
            "entries": {
                record["id"]: [{
                    "source": "Machine",
                    "source_hash": "hash",
                    "translation": "Машина",
                }]
            }
        }

        with tempfile.TemporaryDirectory() as directory:
            count = build_language_files(
                {"records": [record]},
                catalog,
                {},
                Path(directory),
            )
            output = json.loads(
                (
                    Path(directory)
                    / "assets/example/lang/ru_ru.json"
                ).read_text(encoding="utf-8")
            )

        self.assertEqual(1, count)
        self.assertEqual("Машина", output["item.example.machine"])

    def test_intentionally_unchanged_catalog_value_uses_native_resource(self) -> None:
        record = {
            "id": "item:item.example.name",
            "source": "Proper Name",
            "source_hash": "hash",
            "namespace": "example",
            "translation_key": "item.example.name",
            "native_ru_present": True,
            "native_ru_same_as_source": True,
            "output_format": "lang",
        }
        catalog = {
            "entries": {
                record["id"]: [{
                    "source": "Proper Name",
                    "source_hash": "hash",
                    "translation": "Proper Name",
                }]
            }
        }

        with tempfile.TemporaryDirectory() as directory:
            count = build_language_files(
                {"records": [record]},
                catalog,
                {},
                Path(directory),
            )

        self.assertEqual(0, count)

    def test_catalog_does_not_replace_a_real_native_translation(self) -> None:
        record = {
            "id": "item:item.example.machine",
            "source": "Machine",
            "source_hash": "hash",
            "namespace": "example",
            "translation_key": "item.example.machine",
            "native_ru_present": True,
            "output_format": "lang",
        }
        catalog = {
            "entries": {
                record["id"]: [{
                    "source": "Machine",
                    "source_hash": "hash",
                    "translation": "Каталожный перевод",
                }]
            }
        }

        with tempfile.TemporaryDirectory() as directory:
            count = build_language_files(
                {"records": [record]},
                catalog,
                {},
                Path(directory),
            )

        self.assertEqual(0, count)

    def test_runtime_override_has_explicit_precedence_over_catalog(self) -> None:
        record = {
            "id": "item:item.example.machine",
            "source": "Machine",
            "source_hash": "hash",
            "namespace": "example",
            "translation_key": "item.example.machine",
            "native_ru_present": True,
            "native_ru_same_as_source": True,
            "output_format": "lang",
        }
        catalog = {
            "entries": {
                record["id"]: [{
                    "source": "Machine",
                    "source_hash": "hash",
                    "translation": "Машина",
                }]
            }
        }

        with tempfile.TemporaryDirectory() as directory:
            count = build_language_files(
                {"records": [record]},
                catalog,
                {"example": {"item.example.machine": "Механизм"}},
                Path(directory),
            )
            output = json.loads(
                (
                    Path(directory)
                    / "assets/example/lang/ru_ru.json"
                ).read_text(encoding="utf-8")
            )

        self.assertEqual(1, count)
        self.assertEqual("Механизм", output["item.example.machine"])


class RuntimeAuditOverridesTest(unittest.TestCase):
    def test_schema_one_remains_compatible(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "runtime-audit-overrides.json"
            path.write_text(
                json.dumps({
                    "schema": 1,
                    "translations": {
                        "example": {"item.example.machine": "Машина"}
                    },
                }),
                encoding="utf-8",
            )

            policy = load_runtime_audit_overrides(path)

        self.assertEqual({}, policy.accepted_same_as_english)

    def test_loads_translations_and_explicit_english_names(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "runtime-audit-overrides.json"
            path.write_text(
                json.dumps({
                    "schema": 2,
                    "translations": {
                        "example": {"item.example.machine": "Машина"}
                    },
                    "accepted_same_as_english": {
                        "item.example.proper_name": "Proper Name"
                    },
                }),
                encoding="utf-8",
            )

            policy = load_runtime_audit_overrides(path)

        self.assertEqual(
            {"example": {"item.example.machine": "Машина"}},
            policy.translations,
        )
        self.assertEqual(
            {"item.example.proper_name": "Proper Name"},
            policy.accepted_same_as_english,
        )


class ResourcepackOverrideTest(unittest.TestCase):
    def test_merges_language_override_without_dropping_generated_keys(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            resourcepack = root / "resourcepack"
            generated = resourcepack / "assets/example/lang/ru_ru.json"
            generated.parent.mkdir(parents=True)
            generated.write_text(
                json.dumps(
                    {
                        "block.example.machine": "Машина",
                        "item.example.shared": "Старое название",
                    }
                ),
                encoding="utf-8",
            )
            overrides = root / "overrides"
            override = overrides / "assets/example/lang/ru_ru.json"
            override.parent.mkdir(parents=True)
            override.write_text(
                json.dumps(
                    {
                        "item.example.shared": "Новое название",
                        "item.example.extra": "Дополнение",
                    }
                ),
                encoding="utf-8",
            )

            apply_translation_overrides(overrides, resourcepack)
            output = json.loads(generated.read_text(encoding="utf-8"))

        self.assertEqual("Машина", output["block.example.machine"])
        self.assertEqual("Новое название", output["item.example.shared"])
        self.assertEqual("Дополнение", output["item.example.extra"])

    def test_rejects_non_russian_translation_override(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            overrides = root / "overrides"
            override = overrides / "assets/example/lang/en_us.json"
            override.parent.mkdir(parents=True)
            override.write_text('{"item.example.machine": "Machine"}', encoding="utf-8")

            with self.assertRaisesRegex(
                ValueError,
                "translation overrides may only contain ru_ru resources",
            ):
                apply_translation_overrides(overrides, root / "resourcepack")

    def test_compatibility_pack_applies_only_russian_resources(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            compatibility = root / "compatibility"
            resourcepack = root / "resourcepack"
            generated_lang = resourcepack / "assets/example/lang/ru_ru.json"
            generated_lang.parent.mkdir(parents=True)
            generated_lang.write_text(
                '{"item.example.machine": "Машина"}',
                encoding="utf-8",
            )
            for language, value in (
                ("en_us", "Replacement"),
                ("ru_ru", "Замена"),
            ):
                entry = (
                    compatibility
                    / f"assets/example/books/{language}/entry.json"
                )
                entry.parent.mkdir(parents=True)
                entry.write_text(value, encoding="utf-8")
            compatibility_lang = (
                compatibility / "assets/example/lang/ru_ru.json"
            )
            compatibility_lang.parent.mkdir(parents=True, exist_ok=True)
            compatibility_lang.write_text(
                '{"item.example.extra": "Дополнение"}',
                encoding="utf-8",
            )

            copied = apply_compatibility_translations(
                compatibility,
                resourcepack,
            )

            self.assertEqual(2, copied)
            self.assertFalse(
                (
                    resourcepack
                    / "assets/example/books/en_us/entry.json"
                ).exists()
            )
            self.assertEqual(
                "Замена",
                (
                    resourcepack
                    / "assets/example/books/ru_ru/entry.json"
                ).read_text(encoding="utf-8"),
            )
            self.assertEqual(
                {
                    "item.example.extra": "Дополнение",
                    "item.example.machine": "Машина",
                },
                json.loads(generated_lang.read_text(encoding="utf-8")),
            )


if __name__ == "__main__":
    unittest.main()
