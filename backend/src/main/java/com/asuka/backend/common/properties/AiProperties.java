package com.asuka.backend.common.properties;

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

    /** 批内并发数，实际使用时会限制在 1-4；1 表示串行 */
    private int batchConcurrency = 2;

    /** 每次 agent 调用最多模型轮次 */
    private int maxTurns = 6;

    /** 每次 agent 调用最多工具调用次数 */
    private int maxToolCalls = 8;

    /** fake provider 是否额外返回一条无法定位的问题，用于验证降级/部分完成 */
    private boolean fakeInvalidIssue = false;

    private DeepSeek deepseek = new DeepSeek();

    /** 当前 provider 实际使用的模型名称，用于任务和报告快照。 */
    public String getActiveModelName() {
        return "fake".equalsIgnoreCase(provider) ? "fake" : deepseek.getModel();
    }

    @Data
    public static class DeepSeek {
        private String apiKey = "";
        private String baseUrl = "https://api.deepseek.com";
        private String model = "deepseek-flash";
        private int connectTimeoutMs = 10000;
        private int readTimeoutMs = 180000;
        /** DeepSeek V4.1 Flash 的 1M 上下文下，网关实测可接受 128K 输出上限 */
        private int maxTokens = 131072;
        private double temperature = 0.1;
    }
}
