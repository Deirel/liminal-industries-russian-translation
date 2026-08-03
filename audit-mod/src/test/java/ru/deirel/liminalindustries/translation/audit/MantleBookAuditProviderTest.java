package ru.deirel.liminalindustries.translation.audit;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void classifiesPresentFieldsWhenRussianArrayIsShorter() {
        List<AuditSubject> subjects = collect(
            """
                {"text":[{"text":"First"},{"text":"Second"}]}
                """,
            """
                {"text":[{"text":"Первое"}]}
                """
        );

        assertEquals(2, subjects.size());
        assertTrue(subjects.get(0).localizedLiteral());
        assertEquals("Первое", subjects.get(0).name().getString());
        assertFalse(subjects.get(1).localizedLiteral());
        assertEquals("Second", subjects.get(1).name().getString());
    }

    @Test
    void classifiesPresentFieldsWhenRussianArrayIsLonger() {
        List<AuditSubject> subjects = collect(
            """
                {"properties":["First"]}
                """,
            """
                {"properties":["Первое","Дополнительное"]}
                """
        );

        assertEquals(1, subjects.size());
        assertTrue(subjects.get(0).localizedLiteral());
        assertEquals("Первое", subjects.get(0).name().getString());
    }

    private static List<AuditSubject> collect(String english, String russian) {
        List<AuditSubject> subjects = new ArrayList<>();
        new MantleBookAuditProvider().collect(
            ResourceLocation.fromNamespaceAndPath(
                "tconstruct",
                "book/guide/en_us/page.json"
            ),
            "",
            null,
            JsonParser.parseString(english),
            JsonParser.parseString(russian),
            false,
            subjects
        );
        return subjects;
    }
}
