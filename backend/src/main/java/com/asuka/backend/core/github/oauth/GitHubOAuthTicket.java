package com.asuka.backend.core.github.oauth;

import lombok.Data;

/**
 * GitHub 登录一次性 ticket 的 Redis 载荷。
 */
@Data
public class GitHubOAuthTicket {

    private Long userId;
}
