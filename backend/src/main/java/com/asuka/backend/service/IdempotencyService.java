package com.asuka.backend.service;

/**
 * 创建任务幂等键的读取与写入。
 */
public interface IdempotencyService {

    String lookup(String scope, String identity, String key, String requestHash);

    void store(String scope, String identity, String key, String requestHash, String responseJson);
}
