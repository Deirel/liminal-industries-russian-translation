package ru.deirel.liminalindustries.translation.audit;

final class TranslationCoverage {
    private TranslationCoverage() {
    }

    static boolean isTranslated(
        boolean explicitlyPresent,
        String runtimeValue,
        String englishValue
    ) {
        return explicitlyPresent
            || !runtimeValue.equals(englishValue)
            || TranslationTemplate.isLanguageNeutral(runtimeValue);
    }
}
