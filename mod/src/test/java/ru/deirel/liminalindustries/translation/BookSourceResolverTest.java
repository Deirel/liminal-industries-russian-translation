package ru.deirel.liminalindustries.translation;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BookSourceResolverTest {
    @Test
    void changedEffectiveResourceWinsOverOldFallbackHashMatch() {
        byte[] oldSource = bytes("{\"title\":\"Old\"}");
        byte[] changedEffective = bytes("{\"title\":\"Changed\"}");
        BookSourceResolver resolver = BookSourceResolver.effectiveThenFallback(
            (resource, hash) ->
                BookSourceResolver.Resolution.found(changedEffective),
            (resource, hash) -> BookSourceResolver.Resolution.found(oldSource)
        );

        BookSourceResolver.Resolution result = resolver.resolve(
            new BookTranslationIndex.ResourceKey(
                "example",
                "book/en_us/entry.json"
            ),
            BookTranslationAdapter.sha256(oldSource)
        );

        assertArrayEquals(changedEffective, result.bytes());
    }

    @Test
    void effectiveLanguageMergesResourceStackWithoutTranslationBaseline() {
        BookSourceResolver.Resolution result =
            BookSourceResolver.mergeLanguageLayers(List.of(
                new BookSourceResolver.LanguageLayer(
                    "mod_resources",
                    bytes("{\"chapter\":\"Original\",\"other\":\"Kept\"}")
                ),
                new BookSourceResolver.LanguageLayer(
                    "file/compatibility",
                    bytes("{\"chapter\":\"Changed\"}")
                ),
                new BookSourceResolver.LanguageLayer(
                    "liminal_industries_ru_baseline",
                    bytes("{\"chapter\":\"Перевод\",\"other\":\"Перевод\"}")
                )
            ));

        assertEquals(
            JsonParser.parseString(
                "{\"chapter\":\"Changed\",\"other\":\"Kept\"}"
            ),
            JsonParser.parseString(new String(result.bytes(), StandardCharsets.UTF_8))
        );
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
