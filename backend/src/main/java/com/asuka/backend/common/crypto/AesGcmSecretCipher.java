package com.asuka.backend.common.crypto;

import com.asuka.backend.common.exception.BusinessException;
import com.asuka.backend.common.properties.SecurityProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 敏感文本加解密。
 *
 * <p>密文格式：{@code v1:base64(iv||ciphertext||tag)}。每次加密生成随机 12 字节 IV，
 * 相同明文不会得到相同密文。v0 前缀只用于轮换期间读取 previousEncryptionKey 加密的旧数据。
 */
@Slf4j
@Component
public class AesGcmSecretCipher implements SecretCipher {

    private static final String CURRENT_PREFIX = "v1:";
    private static final String PREVIOUS_PREFIX = "v0:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BYTES = 32;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SecurityProperties properties;

    public AesGcmSecretCipher(SecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            throw new BusinessException(500, "待加密内容不能为空");
        }
        byte[] key = decodeKey(properties.getEncryptionKey());
        try {
            byte[] iv = new byte[IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            return CURRENT_PREFIX + Base64.getEncoder().encodeToString(result);
        } catch (Exception ex) {
            log.error("敏感数据加密失败", ex);
            throw new BusinessException(500, "服务端加密失败");
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        if (!StringUtils.hasText(ciphertext)) {
            throw new BusinessException(500, "加密内容为空");
        }
        String keyText;
        String payload;
        if (ciphertext.startsWith(CURRENT_PREFIX)) {
            keyText = properties.getEncryptionKey();
            payload = ciphertext.substring(CURRENT_PREFIX.length());
        } else if (ciphertext.startsWith(PREVIOUS_PREFIX)) {
            keyText = properties.getPreviousEncryptionKey();
            payload = ciphertext.substring(PREVIOUS_PREFIX.length());
        } else {
            throw new BusinessException(500, "加密数据格式不正确");
        }
        byte[] key = decodeKey(keyText);
        try {
            byte[] raw = Base64.getDecoder().decode(payload);
            if (raw.length <= IV_LENGTH) {
                throw new IllegalArgumentException("ciphertext too short");
            }
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(raw, 0, iv, 0, IV_LENGTH);
            byte[] encrypted = new byte[raw.length - IV_LENGTH];
            System.arraycopy(raw, IV_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("敏感数据解密失败，可能是密钥不匹配或数据损坏");
            throw new BusinessException(500, "服务端加密配置错误");
        }
    }

    private byte[] decodeKey(String keyText) {
        if (!StringUtils.hasText(keyText)) {
            throw new BusinessException(500, "服务端加密密钥未配置");
        }
        try {
            byte[] key = Base64.getDecoder().decode(keyText.trim());
            if (key.length != KEY_LENGTH_BYTES) {
                throw new IllegalArgumentException("key length must be 32 bytes");
            }
            return key;
        } catch (IllegalArgumentException ex) {
            log.error("服务端加密密钥格式不正确");
            throw new BusinessException(500, "服务端加密密钥格式不正确");
        }
    }
}
