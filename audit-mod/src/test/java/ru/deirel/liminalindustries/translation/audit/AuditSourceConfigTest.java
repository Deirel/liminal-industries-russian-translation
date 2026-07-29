package ru.deirel.liminalindustries.translation.audit;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AuditSourceConfigTest {
    @Test
    void loadsEnabledProvidersFromVersionConfiguration() {
        assertEquals(
            Set.of(
                "items",
                "blocks",
                "patchouli",
                "immersive_engineering_manual",
                "tconstruct_books"
            ),
            AuditSourceConfig.enabledProviders()
        );
    }

    @Test
    void loadsGeneratedPatchouliAuditIndex() {
        var records = TranslationAuditIndex.patchouliLanguageRecords();

        assertFalse(records.isEmpty());
    }
}
