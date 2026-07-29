package ru.deirel.liminalindustries.translation.audit;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AuditSourceConfigTest {
    @Test
    void nonBookProviderUsesEmptyDefault() {
        AuditProvider provider = new AuditProvider() {
            @Override
            public String id() {
                return "example";
            }

            @Override
            public List<AuditSubject> discover(AuditContext context) {
                return List.of();
            }
        };

        assertEquals(List.of(), provider.bookStacks());
    }

    @Test
    void loadsEnabledProvidersFromVersionConfiguration() {
        Set<String> expected = switch (TranslationAuditIndex.version()) {
            case "1.19.3-original" -> Set.of(
                "items",
                "blocks",
                "patchouli"
            );
            case "1.19.3-7-ae2-fix" -> Set.of(
                "items",
                "blocks",
                "patchouli",
                "immersive_engineering_manual",
                "tconstruct_books"
            );
            default -> throw new AssertionError(
                "unexpected translation version "
                    + TranslationAuditIndex.version()
            );
        };
        assertEquals(
            expected,
            AuditSourceConfig.enabledProviders()
        );
    }

    @Test
    void loadsGeneratedPatchouliAuditIndex() {
        var records = TranslationAuditIndex.patchouliLanguageRecords();

        assertFalse(records.isEmpty());
    }

    @Test
    void generatedIndexContainsEveryTranslatedBook() {
        Set<String> actual = TranslationAuditIndex.allBookRecords().stream()
            .map(record -> record.sourceId() + "=" + record.bookId())
            .collect(Collectors.toSet());
        Set<String> expected = switch (TranslationAuditIndex.version()) {
            case "1.19.3-original" -> Set.of(
                "patchouli=actuallyadditions:booklet",
                "patchouli=botania:lexicon",
                "patchouli=enderio:guide"
            );
            case "1.19.3-7-ae2-fix" -> Set.of(
                "patchouli=actuallyadditions:booklet",
                "patchouli=botania:lexicon",
                "patchouli=dynamictrees:guide",
                "patchouli=enderio:guide",
                "patchouli=parcool:parcool_guide",
                "patchouli=thermal:guidebook",
                "immersive_engineering_manual=immersiveengineering:manual",
                "tconstruct_books=tconstruct:encyclopedia",
                "tconstruct_books=tconstruct:fantastic_foundry",
                "tconstruct_books=tconstruct:materials_and_you",
                "tconstruct_books=tconstruct:mighty_smelting",
                "tconstruct_books=tconstruct:puny_smelting",
                "tconstruct_books=tconstruct:tinkers_gadgetry"
            );
            default -> throw new AssertionError(
                "unexpected translation version "
                    + TranslationAuditIndex.version()
            );
        };

        assertEquals(expected, actual);
    }
}
