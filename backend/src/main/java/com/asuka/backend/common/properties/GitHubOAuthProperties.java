package com.asuka.backend.common.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * GitHub OAuth App 配置。
 *
 * <p>clientSecret 只允许来自环境变量或本地忽略配置，不进入仓库、日志和接口响应。
 * authorize/token/user-api 地址默认指向 GitHub，测试环境可指向本地 mock 服务。
 */
@Data
@Component
@ConfigurationProperties(prefix = "pr.github.oauth")
public class GitHubOAuthProperties {

    private String clientId = "";

    private String clientSecret = "";

    /** 仅 GitHub OAuth 使用的可选 HTTP 代理，空字符串表示直连。 */
    private String proxyHost = "";

    private int proxyPort = 7890;

    /** GitHub OAuth App 中登记的后端回调地址 */
    private String callbackUrl = "http://localhost:8080/api/auth/github/callback";

    /** 登录成功后前端接收一次性 ticket 的页面 */
    private String frontendLoginCallbackUrl = "http://localhost:5173/auth/github/callback";

    /** 绑定完成后前端返回页面 */
    private String frontendBindResultUrl = "http://localhost:5173/settings";

    /** 默认最小 scope：读取公开资料和邮箱 */
    private String scopes = "read:user user:email";

    private String authorizeUrl = "https://github.com/login/oauth/authorize";

    private String tokenUrl = "https://github.com/login/oauth/access_token";

    private String userApiUrl = "https://api.github.com";

    private int connectTimeoutMs = 5000;

    private int readTimeoutMs = 15000;

    private int stateTtlSeconds = 600;

    private int ticketTtlSeconds = 120;

    private String userAgent = "ai-pr-review";
}
