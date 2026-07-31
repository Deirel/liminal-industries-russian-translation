package ru.deirel.liminalindustries.translation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

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

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
