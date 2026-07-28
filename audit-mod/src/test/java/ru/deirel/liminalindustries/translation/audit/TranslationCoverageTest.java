package ru.deirel.liminalindustries.translation.audit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationCoverageTest {
    @Test
    void acceptsExplicitTranslationEvenWhenItMatchesEnglish() {
        assertTrue(TranslationCoverage.isTranslated(true, "AE2", "AE2"));
    }

    @Test
    void acceptsRuntimeGeneratedRussianTranslation() {
        assertTrue(TranslationCoverage.isTranslated(
            false,
            "Дубовый ящик 1x1",
            "Oak Drawer 1x1"
        ));
    }

    @Test
    void acceptsRussianComponentFallback() {
        assertTrue(TranslationCoverage.isTranslated(
            false,
            "Акация ящик 1x1",
            "block.example.acacia_drawer"
        ));
    }

    @Test
    void acceptsLanguageNeutralFallbackTemplate() {
        assertTrue(TranslationCoverage.isTranslated(false, "%1$s-%2$s", "%1$s-%2$s"));
    }

    @Test
    void rejectsEnglishFallback() {
        assertFalse(TranslationCoverage.isTranslated(
            false,
            "Potion of %s",
            "Potion of %s"
        ));
    }
}
