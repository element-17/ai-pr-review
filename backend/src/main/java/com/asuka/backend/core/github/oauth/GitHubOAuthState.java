package com.asuka.backend.core.github.oauth;

import lombok.Data;

/**
 * GitHub OAuth state 的 Redis 载荷。
 */
@Data
public class GitHubOAuthState {

    public static final String MODE_LOGIN = "LOGIN";

    public static final String MODE_BIND = "BIND";

    private String mode;

    private Long userId;
}
