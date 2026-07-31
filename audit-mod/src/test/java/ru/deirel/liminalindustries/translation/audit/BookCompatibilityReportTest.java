package ru.deirel.liminalindustries.translation.audit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class BookCompatibilityReportTest {
    @Test
    void translationModIsOptionalForAuditTests() {
        assertNotNull(BookCompatibilityReport.snapshot());
    }
}
