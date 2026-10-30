package com.asuka.backend.common.crypto;

/**
 * 敏感文本加解密。
 *
 * <p>只用于服务端保存 GitHub token、模型 API key 等不能明文落库的数据；
 * 解密结果不得写入日志、接口响应或前端。
 */
public interface SecretCipher {

    String encrypt(String plaintext);

    String decrypt(String ciphertext);
}
