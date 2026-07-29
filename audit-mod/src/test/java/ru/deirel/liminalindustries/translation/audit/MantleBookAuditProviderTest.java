package ru.deirel.liminalindustries.translation.audit;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class MantleBookAuditProviderTest {
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
}
