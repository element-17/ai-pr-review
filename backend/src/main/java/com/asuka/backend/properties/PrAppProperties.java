package com.asuka.backend.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 平台自身的非敏感配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "pr.app")
public class PrAppProperties {

    /** 前端地址，用于生成游客分享链接 */
    private String frontendBaseUrl = "http://localhost:5173";

    /** 游客任务有效期（天） */
    private int guestTaskTtlDays = 7;
}
