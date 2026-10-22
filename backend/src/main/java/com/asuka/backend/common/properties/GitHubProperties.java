package com.asuka.backend.common.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * GitHub API 配置。
 *
 * <p>token 只用于服务端请求 GitHub，不进入前端、不写入日志。
 * 公开仓库不配置 token 也能访问，但未认证限流很低。
 */
@Data
@Component
@ConfigurationProperties(prefix = "pr.github")
public class GitHubProperties {

    /** 服务端 GitHub Token，可为空 */
    private String token = "";

    /** GitHub REST API 地址 */
    private String apiBaseUrl = "https://api.github.com";

    private int connectTimeoutMs = 5000;

    private int readTimeoutMs = 30000;

    private String userAgent = "ai-pr-review";

    /** PR 预览缓存时间（秒） */
    private int previewCacheSeconds = 60;
}
