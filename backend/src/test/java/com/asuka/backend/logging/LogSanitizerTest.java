package com.asuka.backend.logging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogSanitizerTest {

    @Test
    void masksAuthorizationAndCommonSecrets() {
        String message = "Authorization: Bearer gho_abcdefghijklmnopqrstuvwxyz123456 "
                + "api-key=user_1234567890abcdef password=plain-secret "
                + "\"refreshToken\":\"refresh-value\"";
        String sanitized = LogSanitizer.sanitize(message);

        assertFalse(sanitized.contains("gho_abcdefghijklmnopqrstuvwxyz123456"));
        assertFalse(sanitized.contains("user_1234567890abcdef"));
        assertFalse(sanitized.contains("plain-secret"));
        assertFalse(sanitized.contains("refresh-value"));
        assertTrue(sanitized.contains("***"));
    }

    @Test
    void keepsNormalBusinessFields() {
        String message = "access method=GET path=/api/tasks/1 status=200 durationMs=15 userId=2";
        assertTrue(LogSanitizer.sanitize(message).equals(message));
    }
}
