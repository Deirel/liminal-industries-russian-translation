package ru.deirel.liminalindustries.translation.audit;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MantleBookAuditProviderTest {
    @Test
    void parsesMantleLanguageEntries() throws IOException {
        assertEquals(
            Map.of(
                "intro", "Introduction",
                "materials", "Tier 4 General Materials"
            ),
            MantleBookAuditProvider.parseLanguage(new StringReader("""
                # Book index
                intro=Introduction

                materials=Tier 4 General Materials
                """))
        );
    }

    @Test
    void normalizationIgnoresTranslationsButPreservesTechnicalStructure() {
        JsonElement english = JsonParser.parseString("""
            {
              "title": "Page",
              "text": [{"text": "Description"}],
              "tool_filter": "tconstruct:modifiable"
            }
            """);
        JsonElement translated = JsonParser.parseString("""
            {
              "title": "Страница",
              "text": [{"text": "Описание"}],
              "tool_filter": "tconstruct:modifiable"
            }
            """);
        JsonElement stale = JsonParser.parseString("""
            {
              "title": "Страница",
              "text": [{"text": "Описание"}],
              "tool_filter": "tconstruct:wrong"
            }
            """);

        JsonElement normalizedEnglish =
            MantleBookAuditProvider.normalizeStructure(english, null, false);
        assertEquals(
            normalizedEnglish,
            MantleBookAuditProvider.normalizeStructure(
                translated,
                null,
                false
            )
        );
        assertNotEquals(
            normalizedEnglish,
            MantleBookAuditProvider.normalizeStructure(stale, null, false)
        );
    }

    @Test
    void recognizesAtomicStructuralTranslations() {
        assertEquals(
            Set.of(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                "tconstruct",
                "book/guide/ru_ru/page.json"
            )),
            MantleBookAuditProvider.parseExactOnlyResources(new StringReader("""
                {
                  "resources": [
                    {
                      "exact_only": true,
                      "output": {
                        "namespace": "tconstruct",
                        "path": "book/guide/ru_ru/page.json"
                      }
                    },
                    {
                      "output": {
                        "namespace": "test",
                        "path": "book/ru_ru/regular.json"
                      }
                    }
                  ]
                }
                """))
        );

        JsonElement english = JsonParser.parseString(
            "{\"properties\":[\"First\",\"Second\"]}"
        );
        JsonElement compact = JsonParser.parseString(
            "{\"properties\":[\"Первое и второе\"]}"
        );
        assertTrue(MantleBookAuditProvider.isWholeResourceLocalized(
            english,
            compact,
            true
        ));
        assertFalse(MantleBookAuditProvider.isWholeResourceLocalized(
            english,
            english.deepCopy(),
            true
        ));
    }
}
