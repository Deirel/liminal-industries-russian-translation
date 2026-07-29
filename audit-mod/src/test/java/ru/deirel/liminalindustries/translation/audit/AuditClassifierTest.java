package ru.deirel.liminalindustries.translation.audit;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditClassifierTest {
    @Test
    void translatedKeyPasses() {
        assertEquals(
            AuditStatus.TRANSLATED,
            AuditClassifier.classify(
                "Медная шестерня",
                Set.of("item.example.copper_gear"),
                Set.of("item.example.copper_gear"),
                false
            )
        );
    }

    @Test
    void anyMissingNestedKeyFails() {
        assertEquals(
            AuditStatus.MISSING_RU,
            AuditClassifier.classify(
                "Copper Gear",
                Set.of("item.example.copper_gear", "material.example.copper"),
                Set.of("item.example.copper_gear"),
                false
            )
        );
    }

    @Test
    void unusedNestedKeyDoesNotReachClassifier() {
        assertEquals(
            AuditStatus.TRANSLATED,
            AuditClassifier.classify(
                "Обработанные бамбуковые шипы",
                Set.of("item.example.spikes"),
                Set.of("item.example.spikes"),
                false
            )
        );
    }

    @Test
    void cyrillicLiteralPassesButLatinLiteralRequiresReview() {
        assertEquals(
            AuditStatus.CYRILLIC_LITERAL,
            AuditClassifier.classify(
                "Особый предмет", Set.of(), Set.of(), false
            )
        );
        assertEquals(
            AuditStatus.UNVERIFIABLE_LITERAL,
            AuditClassifier.classify(
                "Special Item", Set.of(), Set.of(), false
            )
        );
    }

    @Test
    void literalFromLocalizedResourcePassesEvenWithoutCyrillic() {
        assertEquals(
            AuditStatus.LOCALIZED_LITERAL,
            AuditClassifier.classify(
                "SquARzY", Set.of(), Set.of(), true
            )
        );
    }

    @Test
    void blankNameIsAnError() {
        assertEquals(
            AuditStatus.ERROR,
            AuditClassifier.classify("", Set.of(), Set.of(), false)
        );
    }

    @Test
    void onlyFailureStatusesFailTheAudit() {
        assertFalse(AuditStatus.TRANSLATED.isFailure());
        assertFalse(AuditStatus.LOCALIZED_LITERAL.isFailure());
        assertFalse(AuditStatus.CYRILLIC_LITERAL.isFailure());
        assertTrue(AuditStatus.MISSING_RU.isFailure());
        assertTrue(AuditStatus.UNVERIFIABLE_LITERAL.isFailure());
        assertTrue(AuditStatus.ERROR.isFailure());
    }
}
