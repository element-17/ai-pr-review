package com.asuka.backend.core.agent.ai;

/**
 * 模型默认参数。
 */
public final class ModelDefaults {

    public static final int OPENAI_MAX_OUTPUT_TOKENS = 131072;

    public static final int ANTHROPIC_MAX_OUTPUT_TOKENS = 8192;

    public static final double DEFAULT_TEMPERATURE = 0.1;

    private ModelDefaults() {
    }
}
