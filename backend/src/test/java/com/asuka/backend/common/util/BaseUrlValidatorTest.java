package com.asuka.backend.common.util;

import com.asuka.backend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BaseUrlValidatorTest {

    @Test
    void rejectsLocalhostAndPrivateAddressesByDefault() {
        assertThrows(BusinessException.class,
                () -> BaseUrlValidator.validate("https://localhost:443/v1", false));
        assertThrows(BusinessException.class,
                () -> BaseUrlValidator.validate("https://127.0.0.1/v1", false));
        assertThrows(BusinessException.class,
                () -> BaseUrlValidator.validate("https://192.168.1.10/v1", false));
    }

    @Test
    void rejectsHttpAndEndpointSuffixByDefault() {
        assertThrows(BusinessException.class,
                () -> BaseUrlValidator.validate("http://api.example.com/v1", false));
        assertThrows(BusinessException.class,
                () -> BaseUrlValidator.validate("https://api.example.com/v1/chat/completions", false));
        assertThrows(BusinessException.class,
                () -> BaseUrlValidator.validate("https://api.example.com/v1/messages", false));
    }

    @Test
    void allowsPrivateAddressOnlyWhenExplicitlyEnabled() {
        assertDoesNotThrow(() -> BaseUrlValidator.validate("http://127.0.0.1:18081/v1", true));
        assertDoesNotThrow(() -> BaseUrlValidator.validate("http://localhost:18081", true));
    }
}
