package com.asuka.backend.common.crypto;

import com.asuka.backend.common.exception.BusinessException;
import com.asuka.backend.common.properties.SecurityProperties;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AesGcmSecretCipherTest {

    @Test
    void encryptsAndDecryptsWithRandomIv() {
        SecurityProperties properties = new SecurityProperties();
        properties.setEncryptionKey(randomKey());
        AesGcmSecretCipher cipher = new AesGcmSecretCipher(properties);

        String first = cipher.encrypt("github-token-value");
        String second = cipher.encrypt("github-token-value");

        assertNotEquals(first, second);
        assertTrue(first.startsWith("v1:"));
        assertEquals("github-token-value", cipher.decrypt(first));
        assertEquals("github-token-value", cipher.decrypt(second));
    }

    @Test
    void rejectsTamperedCiphertext() {
        SecurityProperties properties = new SecurityProperties();
        properties.setEncryptionKey(randomKey());
        AesGcmSecretCipher cipher = new AesGcmSecretCipher(properties);
        String encrypted = cipher.encrypt("secret");
        String tampered = encrypted.substring(0, encrypted.length() - 4) + "AAAA";

        assertThrows(BusinessException.class, () -> cipher.decrypt(tampered));
    }

    @Test
    void supportsPreviousKeyDuringRotation() {
        String oldKey = randomKey();
        String newKey = randomKey();
        SecurityProperties oldProperties = new SecurityProperties();
        oldProperties.setEncryptionKey(oldKey);
        String oldCiphertext = new AesGcmSecretCipher(oldProperties).encrypt("old-secret");
        String oldVersionCiphertext = "v0:" + oldCiphertext.substring("v1:".length());

        SecurityProperties newProperties = new SecurityProperties();
        newProperties.setEncryptionKey(newKey);
        newProperties.setPreviousEncryptionKey(oldKey);
        AesGcmSecretCipher cipher = new AesGcmSecretCipher(newProperties);

        assertEquals("old-secret", cipher.decrypt(oldVersionCiphertext));
        assertTrue(cipher.encrypt("new-secret").startsWith("v1:"));
    }

    @Test
    void failsClearlyWhenKeyMissing() {
        AesGcmSecretCipher cipher = new AesGcmSecretCipher(new SecurityProperties());
        assertThrows(BusinessException.class, () -> cipher.encrypt("secret"));
    }

    private String randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
