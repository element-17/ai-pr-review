package com.asuka.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 分析配置。
 *
 * <p>provider=deepseek 使用真实 DeepSeek API；provider=fake 用于无 Key 的本地联调和自动化测试。
 */
@Data
@Component
@ConfigurationProperties(prefix = "pr.ai")
public class AiProperties {

    private String provider = "deepseek";

    /** 每批文件数，实际使用时会限制在 3-5 */
    private int batchSize = 4;

    /** 每次 agent 调用最多模型轮次 */
    private int maxTurns = 6;

    /** 每次 agent 调用最多工具调用次数 */
    private int maxToolCalls = 8;

    /** fake provider 是否额外返回一条无法定位的问题，用于验证降级/部分完成 */
    private boolean fakeInvalidIssue = false;

    private DeepSeek deepseek = new DeepSeek();

    @Data
    public static class DeepSeek {
        private String apiKey = "";
        private String baseUrl = "https://api.deepseek.com";
        private String model = "deepseek-flash";
        private int connectTimeoutMs = 10000;
        private int readTimeoutMs = 180000;
        private int maxTokens = 8192;
        private double temperature = 0.1;
    }
}
