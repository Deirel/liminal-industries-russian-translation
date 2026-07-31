package ru.deirel.liminalindustries.translation.audit;

final class AuditProvenanceClassifier {
    private static final String BASELINE_PACK =
        "liminal_industries_ru_baseline";

    private AuditProvenanceClassifier() {
    }

    static Result classify(
        boolean targetScope,
        String expectedSource,
        String currentEnglish,
        boolean hasRussianResource,
        boolean runtimeTranslated,
        boolean sameAsEnglish,
        boolean sameAsEnglishAccepted,
        boolean languageNeutral,
        String sourcePack
    ) {
        boolean translated = hasRussianResource
            || runtimeTranslated
            || languageNeutral;
        if (!targetScope) {
            return new Result(AuditProvenance.EXTRA_MOD, translated);
        }
        if (!translated) {
            return new Result(AuditProvenance.MISSING, false);
        }
        if (isUserPack(sourcePack)) {
            return new Result(AuditProvenance.USER_OVERRIDE, true);
        }
        if (BASELINE_PACK.equals(sourcePack)
            && expectedSource != null
            && currentEnglish != null
            && !expectedSource.equals(currentEnglish)) {
            return new Result(AuditProvenance.SOURCE_CHANGED, false);
        }
        if (sameAsEnglish
            && !sameAsEnglishAccepted
            && !languageNeutral) {
            return new Result(AuditProvenance.SAME_AS_ENGLISH, false);
        }
        if (BASELINE_PACK.equals(sourcePack)) {
            return new Result(AuditProvenance.OUR_TRANSLATION, true);
        }
        return new Result(AuditProvenance.NATIVE_TRANSLATION, true);
    }

    static boolean isUserPack(String sourcePack) {
        return sourcePack != null
            && (sourcePack.startsWith("file/")
                || sourcePack.startsWith("server")
                || sourcePack.startsWith("world"));
    }

    record Result(AuditProvenance provenance, boolean accepted) {
    }
}
