package ru.deirel.liminalindustries.translation.audit;

import java.util.Set;
import java.util.regex.Pattern;

final class AuditClassifier {
    private static final Pattern CYRILLIC = Pattern.compile("\\p{IsCyrillic}");

    private AuditClassifier() {
    }

    static AuditStatus classify(
        String displayName,
        Set<String> translationKeys,
        Set<String> russianKeys,
        boolean localizedLiteral
    ) {
        if (displayName == null || displayName.isBlank()) {
            return AuditStatus.ERROR;
        }
        if (!translationKeys.isEmpty()) {
            return russianKeys.containsAll(translationKeys)
                ? AuditStatus.TRANSLATED
                : AuditStatus.MISSING_RU;
        }
        if (localizedLiteral) {
            return AuditStatus.LOCALIZED_LITERAL;
        }
        return CYRILLIC.matcher(displayName).find()
            ? AuditStatus.CYRILLIC_LITERAL
            : AuditStatus.UNVERIFIABLE_LITERAL;
    }
}
