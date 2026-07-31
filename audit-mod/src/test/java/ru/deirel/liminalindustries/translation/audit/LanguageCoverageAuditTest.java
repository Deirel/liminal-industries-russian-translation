package ru.deirel.liminalindustries.translation.audit;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LanguageCoverageAuditTest {
    private static final RussianLanguageIndex.ResourceOrigin MOD =
        new RussianLanguageIndex.ResourceOrigin(
            "immersiveengineering",
            "mod/immersiveengineering"
        );
    private static final RussianLanguageIndex.ResourceOrigin OTHER_MOD =
        new RussianLanguageIndex.ResourceOrigin("other", "mod/other");
    private static final RussianLanguageIndex.ResourceOrigin ENGLISH_ONLY =
        new RussianLanguageIndex.ResourceOrigin(
            "english_only",
            "mod/english_only"
        );

    @Test
    void findsMissingKeysInAnyPartiallyTranslatedMod() {
        RussianLanguageIndex english = index(
            Map.of(
                "manual.immersiveengineering.general", "General",
                "manual.immersiveengineering.early_machines",
                    "Workbenches & Furnaces",
                "guide.other.summary", "Summary",
                "guide.other.details", "Details",
                "guide.english_only.title", "Guide"
            ),
            Map.of(
                "manual.immersiveengineering.general", MOD,
                "manual.immersiveengineering.early_machines", MOD,
                "guide.other.summary", OTHER_MOD,
                "guide.other.details", OTHER_MOD,
                "guide.english_only.title", ENGLISH_ONLY
            ),
            Set.of(MOD, OTHER_MOD, ENGLISH_ONLY)
        );
        RussianLanguageIndex russian = index(
            Map.of(
                "manual.immersiveengineering.general", "Общие сведения",
                "guide.other.summary", "Сводка"
            ),
            Map.of(
                "manual.immersiveengineering.general", MOD,
                "guide.other.summary", OTHER_MOD
            ),
            Set.of(MOD, OTHER_MOD)
        );

        LanguageCoverageAudit.Result result = LanguageCoverageAudit.inspect(
            english,
            russian,
            Map.of(
                "manual.immersiveengineering.general", "Общие сведения",
                "manual.immersiveengineering.early_machines",
                    "Workbenches & Furnaces",
                "guide.other.summary", "Сводка",
                "guide.other.details", "Details",
                "guide.english_only.title", "Guide"
            ),
            Map.of()
        );

        assertEquals(4, result.checkedKeys());
        assertEquals(
            Set.of(
                "manual.immersiveengineering.early_machines",
                "guide.other.details"
            ),
            result.gaps().stream()
                .map(LanguageCoverageAudit.Gap::key)
                .collect(java.util.stream.Collectors.toSet())
        );
    }

    @Test
    void acceptsRuntimeTranslationsNeutralTemplatesAndReviewedEnglish() {
        RussianLanguageIndex english = index(
            Map.of(
                "example.runtime", "Runtime text",
                "example.neutral", "%1$s / %2$s",
                "example.reviewed", "API"
            ),
            Map.of(
                "example.runtime", MOD,
                "example.neutral", MOD,
                "example.reviewed", MOD
            ),
            Set.of(MOD)
        );
        RussianLanguageIndex russian = index(Map.of(), Map.of(), Set.of(MOD));

        LanguageCoverageAudit.Result result = LanguageCoverageAudit.inspect(
            english,
            russian,
            Map.of("example.runtime", "Текст времени выполнения"),
            Map.of("example.reviewed", "API")
        );

        assertEquals(3, result.checkedKeys());
        assertEquals(0, result.gaps().size());
    }

    @Test
    void ignoresUserOwnedLanguagePairs() {
        RussianLanguageIndex.ResourceOrigin user =
            new RussianLanguageIndex.ResourceOrigin("example", "file/custom");
        RussianLanguageIndex english = index(
            Map.of("example.key", "Custom"),
            Map.of("example.key", user),
            Set.of(user)
        );
        RussianLanguageIndex russian = index(Map.of(), Map.of(), Set.of(user));

        LanguageCoverageAudit.Result result = LanguageCoverageAudit.inspect(
            english,
            russian,
            Map.of("example.key", "Custom"),
            Map.of()
        );

        assertEquals(0, result.checkedKeys());
        assertEquals(0, result.gaps().size());
    }

    private static RussianLanguageIndex index(
        Map<String, String> values,
        Map<String, RussianLanguageIndex.ResourceOrigin> origins,
        Set<RussianLanguageIndex.ResourceOrigin> resources
    ) {
        return RussianLanguageIndex.of(values, origins, resources);
    }
}
