package com.asuka.backend.common.constant;

/**
 * 模型请求格式。
 */
public final class ModelProviderConstant {

    /** OpenAI Chat Completions 兼容格式 */
    public static final String OPENAI_CHAT = "OPENAI_CHAT";

    /** Anthropic Messages 兼容格式 */
    public static final String ANTHROPIC_MESSAGES = "ANTHROPIC_MESSAGES";

    /** 环境兜底的确定性 fake 客户端，不写入 ai_model_config */
    public static final String FAKE = "FAKE";

    public static boolean isValid(String provider) {
        return OPENAI_CHAT.equals(provider) || ANTHROPIC_MESSAGES.equals(provider);
    }

    private ModelProviderConstant() {
    }
}
