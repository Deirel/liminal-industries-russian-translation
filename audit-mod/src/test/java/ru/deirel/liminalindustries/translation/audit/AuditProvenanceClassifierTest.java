package ru.deirel.liminalindustries.translation.audit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditProvenanceClassifierTest {
    @Test
    void distinguishesOurNativeAndUserTranslations() {
        assertEquals(
            AuditProvenance.OUR_TRANSLATION,
            classify("English", "English", true, false, false,
                "liminal_industries_ru_baseline").provenance()
        );
        assertEquals(
            AuditProvenance.NATIVE_TRANSLATION,
            classify("English", "English", true, false, false,
                "mod_resources").provenance()
        );
        assertEquals(
            AuditProvenance.NATIVE_TRANSLATION,
            classify("English", "English", true, false, false,
                "example-mod-1.0.jar").provenance()
        );
        AuditProvenanceClassifier.Result user = classify(
            "English",
            "Changed English",
            true,
            true,
            false,
            "file/custom"
        );
        assertEquals(AuditProvenance.USER_OVERRIDE, user.provenance());
        assertTrue(user.accepted());
    }

    @Test
    void sourceChangesAndEnglishCopiesAreNotAcceptedAsCoverage() {
        AuditProvenanceClassifier.Result changed = classify(
            "Expected",
            "Changed",
            true,
            false,
            false,
            "liminal_industries_ru_baseline"
        );
        assertEquals(AuditProvenance.SOURCE_CHANGED, changed.provenance());
        assertFalse(changed.accepted());

        AuditProvenanceClassifier.Result englishCopy = classify(
            "English",
            "English",
            true,
            true,
            false,
            "mod_resources"
        );
        assertEquals(
            AuditProvenance.SAME_AS_ENGLISH,
            englishCopy.provenance()
        );
        assertFalse(englishCopy.accepted());
    }

    @Test
    void updatedNativeTranslationMayFollowItsUpdatedEnglishSource() {
        AuditProvenanceClassifier.Result result = classify(
            "Old English",
            "Updated English",
            true,
            false,
            false,
            "example-mod-1.1.jar"
        );

        assertEquals(AuditProvenance.NATIVE_TRANSLATION, result.provenance());
        assertTrue(result.accepted());
    }

    @Test
    void userMayIntentionallyOverrideWithEnglish() {
        AuditProvenanceClassifier.Result result = classify(
            "English",
            "English",
            true,
            true,
            false,
            "file/english-names"
        );

        assertEquals(AuditProvenance.USER_OVERRIDE, result.provenance());
        assertTrue(result.accepted());
    }

    @Test
    void reviewedEnglishNameIsAcceptedAsNativeCoverage() {
        AuditProvenanceClassifier.Result result =
            AuditProvenanceClassifier.classify(
                true,
                "Proper Name",
                "Proper Name",
                true,
                false,
                true,
                true,
                false,
                "example-mod-1.0.jar"
            );

        assertEquals(AuditProvenance.NATIVE_TRANSLATION, result.provenance());
        assertTrue(result.accepted());
    }

    @Test
    void extraModIsOutsideTargetCoverage() {
        AuditProvenanceClassifier.Result result =
            AuditProvenanceClassifier.classify(
                false,
                null,
                "English",
                false,
                false,
                false,
                false,
                false,
                null
            );

        assertEquals(AuditProvenance.EXTRA_MOD, result.provenance());
    }

    private static AuditProvenanceClassifier.Result classify(
        String expected,
        String english,
        boolean hasRussian,
        boolean sameAsEnglish,
        boolean neutral,
        String pack
    ) {
        return AuditProvenanceClassifier.classify(
            true,
            expected,
            english,
            hasRussian,
            false,
            sameAsEnglish,
            false,
            neutral,
            pack
        );
    }
}
