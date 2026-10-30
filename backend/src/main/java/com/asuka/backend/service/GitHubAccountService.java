package com.asuka.backend.service;

import com.asuka.backend.core.github.oauth.GitHubOAuthUser;

/**
 * GitHub 登录账号匹配/创建。
 */
public interface GitHubAccountService {

    /**
     * 已有绑定则登录原账号；没有绑定则创建新本地账号并绑定。
     */
    Long loginOrRegister(GitHubOAuthUser user, String accessToken, String scope);
}
