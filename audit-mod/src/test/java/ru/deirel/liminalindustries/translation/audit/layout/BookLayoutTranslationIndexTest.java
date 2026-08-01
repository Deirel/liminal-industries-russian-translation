package ru.deirel.liminalindustries.translation.audit.layout;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BookLayoutTranslationIndexTest {
    @Test
    void resolvesOnlyOneExactTranslatedField() throws Exception {
        BookLayoutTranslationIndex index = BookLayoutTranslationIndex.parse(
            new ByteArrayInputStream("""
                {
                  "schema": 1,
                  "resources": [{
                    "output": {"namespace": "test", "path": "book/ru_ru/page.json"},
                    "fields": [
                      {"pointer": "/title", "source": "Title", "translation": "Заголовок"},
                      {"pointer": "/first", "source": "First", "translation": "Повтор"},
                      {"pointer": "/second", "source": "Second", "translation": "Повтор"}
                    ]
                  }]
                }
                """.getBytes(StandardCharsets.UTF_8))
        );

        assertEquals(
            "json_pointer:/title",
            index.source("assets/test/book/ru_ru/page.json", "Заголовок")
        );
        assertEquals(
            "json_pointer:/title",
            index.source("assets/test/book/ru_ru/page.json", "Title")
        );
        assertEquals(
            "json_pointer:/title",
            index.source(
                "assets/test/book/ru_ru/page.json",
                "Заголовок",
                "Очищенный заголовок"
            )
        );
        assertNull(index.source("assets/test/book/ru_ru/page.json", "Повтор"));
        assertNull(index.source("assets/test/book/ru_ru/page.json", "Динамический"));
    }

    @Test
    void resolvesPointersFromTheEffectiveRuntimeResource() {
        var resource = JsonParser.parseString("""
            {
              "text": [{"text": "Фактически загруженный текст"}],
              "a/b~c": "Экранированный указатель"
            }
            """);

        assertEquals(
            "json_pointer:/text/0/text",
            BookLayoutTranslationIndex.source(
                resource,
                "Фактически загруженный текст",
                "Отрисованный текст"
            )
        );
        assertEquals(
            "json_pointer:/a~1b~0c",
            BookLayoutTranslationIndex.source(
                resource,
                "Экранированный указатель",
                "Отрисованный текст"
            )
        );
    }
}
