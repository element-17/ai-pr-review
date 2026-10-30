package com.asuka.backend.core.agent.ai;

/**
 * 单一 provider 格式的模型客户端。
 */
public interface ProviderModelClient {

    ModelResponse chat(ModelRequest request, ModelRuntimeConfig config);
}
