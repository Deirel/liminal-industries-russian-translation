package ru.deirel.liminalindustries.translation;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookTranslationAdapterTest {
    @Test
    void exactSourceReturnsPackagedResourceByteForByte() {
        byte[] source = bytes("{\"title\":\"Source\"}");
        byte[] translated = bytes("{ \"title\": \"Перевод\" }\\n");
        BookTranslationAdapter.Result result = BookTranslationAdapter.adapt(
            rule(
                BookTranslationIndex.Format.JSON,
                BookTranslationAdapter.sha256(source),
                field("title", "Source", "/title", null, null, "Перевод")
            ),
            source,
            translated
        );

        assertTrue(result.exactSource());
        assertArrayEquals(translated, result.bytes());
        assertEquals(1, result.translatedFields());
        assertTrue(result.skippedFieldIds().isEmpty());
    }

    @Test
    void patchouliInsertedPageKeepsNewTextAndMovesUnchangedTranslations() {
        byte[] source = bytes("""
            {
              "pages": [
                {"type": "image", "text": "New upstream page"},
                {"type": "text", "text": "Old page"}
              ]
            }
            """);
        BookTranslationAdapter.Result result = BookTranslationAdapter.adapt(
            rule(
                BookTranslationIndex.Format.JSON,
                "sha256:old",
                field(
                    "old-page",
                    "Old page",
                    "/pages/0/text",
                    null,
                    null,
                    "Старая страница"
                )
            ),
            source,
            bytes("""
                {"pages":[{"type":"text","text":"Старая страница"}]}
                """)
        );
        String output = text(result.bytes());

        assertTrue(output.contains("New upstream page"));
        assertTrue(output.contains("Старая страница"));
        assertEquals(1, result.translatedFields());
        assertTrue(result.skippedFieldIds().isEmpty());
    }

    @Test
    void changedPatchouliFieldIsNotOverwritten() {
        byte[] source = bytes("{\"title\":\"Changed upstream\"}");
        BookTranslationAdapter.Result result = BookTranslationAdapter.adapt(
            rule(
                BookTranslationIndex.Format.JSON,
                "sha256:old",
                field("title", "Old title", "/title", null, null, "Старый")
            ),
            source,
            bytes("{\"title\":\"Старый\"}")
        );

        assertTrue(text(result.bytes()).contains("Changed upstream"));
        assertEquals(List.of("title"), result.skippedFieldIds());
    }

    @Test
    void changedFieldIsNotRelocatedToAnUnrelatedMatchingField() {
        byte[] source = bytes("""
            {
              "type": "entry",
              "title": "Changed upstream",
              "pages": [{"type": "page", "title": "Old title"}]
            }
            """);
        BookTranslationAdapter.Result result = BookTranslationAdapter.adapt(
            rule(
                BookTranslationIndex.Format.JSON,
                "sha256:old",
                field("title", "Old title", "/title", null, null, "Старый")
            ),
            source,
            bytes("{\"type\":\"entry\",\"title\":\"Старый\"}")
        );

        assertFalse(text(result.bytes()).contains("\"title\": \"Старый\""));
        assertEquals(List.of("title"), result.skippedFieldIds());
    }

    @Test
    void duplicateMovedJsonTextIsSkippedAsAmbiguous() {
        byte[] source = bytes("""
            {"pages":[{"text":"Repeated"},{"text":"Repeated"}]}
            """);
        BookTranslationAdapter.Result result = BookTranslationAdapter.adapt(
            rule(
                BookTranslationIndex.Format.JSON,
                "sha256:old",
                field(
                    "repeated",
                    "Repeated",
                    "/pages/4/text",
                    null,
                    null,
                    "Повтор"
                )
            ),
            source,
            bytes("""
                {"pages":[{},{},{},{},{"text":"Повтор"}]}
                """)
        );

        assertFalse(text(result.bytes()).contains("Повтор"));
        assertEquals(List.of("repeated"), result.skippedFieldIds());
    }

    @Test
    void mantleJsonUsesTheSameFieldLevelSafety() {
        byte[] source = bytes("""
            {"data":[{"type":"image","text":"Inserted"},{"type":"tool","text":"Tool properties"}]}
            """);
        BookTranslationAdapter.Result result = BookTranslationAdapter.adapt(
            rule(
                BookTranslationIndex.Format.JSON,
                "sha256:old",
                field(
                    "mantle",
                    "Tool properties",
                    "/data/0/text",
                    null,
                    null,
                    "Свойства инструмента"
                )
            ),
            source,
            bytes("""
                {"data":[{"type":"tool","text":"Свойства инструмента"}]}
                """)
        );

        assertTrue(text(result.bytes()).contains("Inserted"));
        assertTrue(text(result.bytes()).contains("Свойства инструмента"));
    }

    @Test
    void structuralOverrideIsAtomicAndRequiresExactSource() {
        byte[] exactSource = bytes("{\"properties\":[\"First\",\"Second\"]}");
        byte[] compact = bytes("{\"properties\":[\"Первое и второе\"]}");
        BookTranslationIndex.Rule rule = new BookTranslationIndex.Rule(
            BookTranslationIndex.Format.JSON,
            new BookTranslationIndex.ResourceKey("example", "en_us/source"),
            BookTranslationAdapter.sha256(exactSource),
            ResourceLocation.fromNamespaceAndPath("example", "ru_ru/output"),
            List.of(),
            true,
            List.of("first", "second")
        );

        BookTranslationAdapter.Result exact = BookTranslationAdapter.adapt(
            rule,
            exactSource,
            compact
        );
        byte[] changedSource = bytes(
            "{\"properties\":[\"Changed\",\"Second\"]}"
        );
        BookTranslationAdapter.Result changed = BookTranslationAdapter.adapt(
            rule,
            changedSource,
            compact
        );

        assertTrue(exact.exactSource());
        assertArrayEquals(compact, exact.bytes());
        assertEquals(2, exact.translatedFields());
        assertFalse(changed.exactSource());
        assertArrayEquals(changedSource, changed.bytes());
        assertEquals(List.of("first", "second"), changed.skippedFieldIds());
    }

    @Test
    void ieManualRelocatesUniqueLinesAndSkipsChangedLines() {
        byte[] source = bytes("Inserted\nOriginal line\nChanged line\n");
        BookTranslationAdapter.Result result = BookTranslationAdapter.adapt(
            rule(
                BookTranslationIndex.Format.LINES,
                "sha256:old",
                field(
                    "moved",
                    "Original line",
                    null,
                    0,
                    null,
                    "Исходная строка"
                ),
                field(
                    "changed",
                    "Old line",
                    null,
                    1,
                    null,
                    "Старая строка"
                )
            ),
            source,
            null
        );

        assertEquals(
            "Inserted\nИсходная строка\nChanged line\n",
            text(result.bytes())
        );
        assertEquals(List.of("changed"), result.skippedFieldIds());
    }

    @Test
    void ieManualPreservesCrLfAndFinalNewline() {
        byte[] source = bytes("Inserted\r\nOriginal line\r\n");
        BookTranslationAdapter.Result result = BookTranslationAdapter.adapt(
            rule(
                BookTranslationIndex.Format.LINES,
                "sha256:old",
                field(
                    "moved",
                    "Original line",
                    null,
                    0,
                    null,
                    "Исходная строка"
                )
            ),
            source,
            null
        );

        assertEquals(
            "Inserted\r\nИсходная строка\r\n",
            text(result.bytes())
        );
    }

    @Test
    void mantleLanguageMatchesStableKeysAndKeepsNewAndChangedValues() {
        byte[] source = bytes("""
            # comment
            book.title=Book
            book.changed=Updated
            book.new=New
            """);
        BookTranslationAdapter.Result result = BookTranslationAdapter.adapt(
            rule(
                BookTranslationIndex.Format.PROPERTIES,
                "sha256:old",
                field(
                    "title",
                    "Book",
                    null,
                    null,
                    "book.title",
                    "Книга"
                ),
                field(
                    "changed",
                    "Old",
                    null,
                    null,
                    "book.changed",
                    "Старое"
                )
            ),
            source,
            null
        );

        assertEquals(
            "# comment\nbook.title=Книга\nbook.changed=Updated\nbook.new=New\n",
            text(result.bytes())
        );
        assertEquals(List.of("changed"), result.skippedFieldIds());
    }

    @Test
    void mantleLanguagePreservesCrLfWithoutAddingFinalNewline() {
        byte[] source = bytes(
            "book.title=Book\r\nbook.changed=Updated"
        );
        BookTranslationAdapter.Result result = BookTranslationAdapter.adapt(
            rule(
                BookTranslationIndex.Format.PROPERTIES,
                "sha256:old",
                field(
                    "title",
                    "Book",
                    null,
                    null,
                    "book.title",
                    "Книга"
                )
            ),
            source,
            null
        );

        assertEquals(
            "book.title=Книга\r\nbook.changed=Updated",
            text(result.bytes())
        );
    }

    @Test
    void patchouliLanguageDropsOnlyKeysWhoseEnglishSourceChanged() {
        byte[] english = bytes("""
            {"book.title":"Book","book.changed":"Updated"}
            """);
        byte[] russian = bytes("""
            {
              "item.example": "Предмет",
              "book.title": "Книга",
              "book.changed": "Старое"
            }
            """);
        BookTranslationAdapter.Result result = BookTranslationAdapter.adapt(
            rule(
                BookTranslationIndex.Format.LANGUAGE_JSON,
                null,
                field(
                    "title",
                    "Book",
                    null,
                    null,
                    "book.title",
                    "Книга"
                ),
                field(
                    "changed",
                    "Old",
                    null,
                    null,
                    "book.changed",
                    "Старое"
                )
            ),
            english,
            russian
        );
        String output = text(result.bytes());

        assertTrue(output.contains("\"item.example\": \"Предмет\""));
        assertTrue(output.contains("\"book.title\": \"Книга\""));
        assertFalse(output.contains("book.changed"));
        assertEquals(List.of("changed"), result.skippedFieldIds());
    }

    @Test
    void patchouliLanguageExactFieldsReturnPackagedBytes() {
        byte[] english = bytes("{\"book.title\":\"Book\"}");
        byte[] russian = bytes("{ \"book.title\": \"Книга\" }");
        BookTranslationAdapter.Result result = BookTranslationAdapter.adapt(
            rule(
                BookTranslationIndex.Format.LANGUAGE_JSON,
                null,
                field(
                    "title",
                    "Book",
                    null,
                    null,
                    "book.title",
                    "Книга"
                )
            ),
            english,
            russian
        );

        assertTrue(result.exactSource());
        assertArrayEquals(russian, result.bytes());
    }

    private static BookTranslationIndex.Rule rule(
        BookTranslationIndex.Format format,
        String sourceSha256,
        BookTranslationIndex.Field... fields
    ) {
        return new BookTranslationIndex.Rule(
            format,
            new BookTranslationIndex.ResourceKey("example", "en_us/source"),
            sourceSha256,
            ResourceLocation.fromNamespaceAndPath("example", "ru_ru/output"),
            List.of(fields),
            false,
            List.of(fields).stream()
                .map(BookTranslationIndex.Field::id)
                .toList()
        );
    }

    private static BookTranslationIndex.Field field(
        String id,
        String source,
        String pointer,
        Integer line,
        String key,
        String translation
    ) {
        return new BookTranslationIndex.Field(
            id,
            source,
            pointer,
            line,
            key,
            translation
        );
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }
}
