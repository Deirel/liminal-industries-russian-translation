package ru.deirel.liminalindustries.translation.audit;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationTemplateTest {
    @Test
    void findsOnlyArgumentsReferencedByPrintfTemplate() {
        assertEquals(
            Set.of(0, 2),
            TranslationTemplate.referencedArguments("%1$s: %3$s")
        );
    }

    @Test
    void findsSequentialAndMessageFormatArguments() {
        assertEquals(
            Set.of(0, 1),
            TranslationTemplate.referencedArguments("%s {1}")
        );
    }

    @Test
    void recognizesLanguageNeutralFormatting() {
        assertTrue(TranslationTemplate.isLanguageNeutral("%1$s-%2$s"));
        assertTrue(TranslationTemplate.isLanguageNeutral("%s × %s"));
        assertFalse(TranslationTemplate.isLanguageNeutral("Potion of %s"));
        assertFalse(TranslationTemplate.isLanguageNeutral("Зелье %s"));
    }
}
