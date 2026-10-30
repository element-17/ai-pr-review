package com.asuka.backend.common.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 服务端敏感数据加密配置。
 *
 * <p>encryptionKey 与 previousEncryptionKey 都是 Base64 编码的 32 字节 AES 密钥。
 * 当前密钥用于新写入，previous 密钥只用于轮换期间解密旧数据。
 */
@Data
@Component
@ConfigurationProperties(prefix = "pr.security")
public class SecurityProperties {

    /** 当前 AES-256 密钥，Base64 编码 */
    private String encryptionKey = "";

    /** 轮换前的 AES-256 密钥，Base64 编码；只用于解密旧数据 */
    private String previousEncryptionKey = "";
}
