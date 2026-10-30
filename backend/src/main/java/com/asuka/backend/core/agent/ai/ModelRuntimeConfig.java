package com.asuka.backend.core.agent.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * 单次模型调用的运行时配置。
 *
 * <p>包含解密后的 API Key，只允许在服务端内存中使用；toString 永远不输出 Key。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelRuntimeConfig {

    /** OPENAI_CHAT / ANTHROPIC_MESSAGES */
    private String provider;

    private String baseUrl;

    @ToString.Exclude
    private String apiKey;

    private String modelId;

    private Integer maxOutputTokens;

    private BigDecimal temperature;

    private int connectTimeoutMs;

    private int readTimeoutMs;

    /** 只有支持 thinking 参数的环境兜底 DeepSeek 才为 true */
    private boolean sendThinking;
}
