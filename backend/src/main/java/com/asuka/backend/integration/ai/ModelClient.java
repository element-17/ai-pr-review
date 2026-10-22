package com.asuka.backend.integration.ai;

/**
 * 模型客户端抽象。
 */
public interface ModelClient {

    ModelResponse chat(ModelRequest request);
}
