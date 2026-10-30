package com.asuka.backend.core.agent.ai;

import com.asuka.backend.common.constant.ModelProviderConstant;
import com.asuka.backend.common.exception.ModelException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 按任务解析出的 provider 路由到对应模型客户端。
 *
 * <p>数据库配置只允许 OPENAI_CHAT/ANTHROPIC_MESSAGES；FAKE 只用于环境兜底和测试。
 */
@Component
@RequiredArgsConstructor
public class ModelClientRouter {

    private final OpenAiChatModelClient openAiChatModelClient;
    private final AnthropicMessagesModelClient anthropicMessagesModelClient;
    private final FakeModelClient fakeModelClient;

    public ModelResponse chat(ModelRequest request, ResolvedModel resolvedModel) {
        if (resolvedModel == null || resolvedModel.getRuntimeConfig() == null) {
            throw new ModelException(ModelException.Kind.NOT_CONFIGURED, "任务模型未配置");
        }
        ModelRuntimeConfig config = resolvedModel.getRuntimeConfig();
        return switch (config.getProvider()) {
            case ModelProviderConstant.OPENAI_CHAT -> openAiChatModelClient.chat(request, config);
            case ModelProviderConstant.ANTHROPIC_MESSAGES -> anthropicMessagesModelClient.chat(request, config);
            case ModelProviderConstant.FAKE -> fakeModelClient.chat(request, config);
            default -> throw new ModelException(ModelException.Kind.NOT_CONFIGURED, "不支持的模型 provider");
        };
    }
}
