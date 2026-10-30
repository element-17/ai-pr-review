package com.asuka.backend.service;

import com.asuka.backend.core.github.oauth.GitHubOAuthUser;
import com.asuka.backend.pojo.entity.UserGitHubBinding;
import com.asuka.backend.pojo.vo.GitHubBindingStatusVO;

/**
 * 用户 GitHub 绑定与 token 管理。
 */
public interface GitHubBindingService {

    UserGitHubBinding findByUserId(Long userId);

    UserGitHubBinding findByGithubId(Long githubId);

    GitHubBindingStatusVO getStatus(Long userId);

    void bind(Long userId, GitHubOAuthUser user, String accessToken, String scope);

    /**
     * 返回用户绑定的 GitHub token；未绑定时返回 null。
     * 已绑定但失效/无法解密时抛出 403，并携带 githubAuthRequired 标记。
     */
    String getActiveAccessTokenOrNull(Long userId);

    void markInvalid(Long userId);

    void markValidated(Long userId);

    void unbind(Long userId);
}
