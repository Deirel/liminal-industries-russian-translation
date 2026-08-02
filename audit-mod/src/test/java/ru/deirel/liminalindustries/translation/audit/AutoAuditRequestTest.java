package ru.deirel.liminalindustries.translation.audit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AutoAuditRequestTest {
    @Test
    void parsesSupportedRequest() {
        AutoAuditRequest request = AutoAuditRequest.parse(
            "{\"schema\":1,\"audit\":\"mantle\",\"exitWhenDone\":true}"
        );

        assertEquals("mantle", request.audit());
        assertTrue(request.exitWhenDone());
    }

    @Test
    void rejectsUnknownAudit() {
        assertThrows(
            IllegalArgumentException.class,
            () -> AutoAuditRequest.parse(
                "{\"schema\":1,\"audit\":\"unknown\",\"exitWhenDone\":true}"
            )
        );
    }
}
