package ru.deirel.liminalindustries.translation.audit;

enum AuditStatus {
    TRANSLATED,
    CYRILLIC_LITERAL,
    MISSING_RU,
    UNVERIFIABLE_LITERAL,
    ERROR;

    boolean isFailure() {
        return this == MISSING_RU || this == UNVERIFIABLE_LITERAL || this == ERROR;
    }
}
