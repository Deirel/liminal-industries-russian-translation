package ru.deirel.liminalindustries.translation.audit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class LanguageCoverageAudit {
    private LanguageCoverageAudit() {
    }

    static Result inspect(
        RussianLanguageIndex english,
        RussianLanguageIndex russian,
        Map<String, String> runtimeRussian,
        Map<String, String> acceptedSameAsEnglish
    ) {
        List<Gap> gaps = new ArrayList<>();
        int checkedKeys = 0;
        for (Map.Entry<String, String> entry : english.values().entrySet()) {
            String key = entry.getKey();
            String englishValue = entry.getValue();
            RussianLanguageIndex.ResourceOrigin origin =
                english.origins().get(key);
            if (origin == null
                || AuditProvenanceClassifier.isUserPack(origin.sourcePack())
                || !russian.hasResource(origin)) {
                continue;
            }
            checkedKeys++;
            if (russian.values().containsKey(key)
                || runtimeTranslationExists(key, englishValue, runtimeRussian)
                || TranslationTemplate.isLanguageNeutral(englishValue)
                || englishValue.equals(acceptedSameAsEnglish.get(key))) {
                continue;
            }
            gaps.add(new Gap(
                key,
                englishValue,
                origin.namespace(),
                origin.sourcePack()
            ));
        }
        gaps.sort(Comparator.comparing(Gap::key));
        return new Result(checkedKeys, List.copyOf(gaps));
    }

    private static boolean runtimeTranslationExists(
        String key,
        String englishValue,
        Map<String, String> runtimeRussian
    ) {
        String runtimeValue = runtimeRussian.get(key);
        return runtimeValue != null && !runtimeValue.equals(englishValue);
    }

    record Gap(
        String key,
        String englishValue,
        String namespace,
        String sourcePack
    ) {
    }

    record Result(int checkedKeys, List<Gap> gaps) {
    }
}
