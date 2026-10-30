package com.asuka.backend.service;

import com.asuka.backend.common.exception.GitHubApiException;
import com.asuka.backend.core.github.GitHubAuth;
import com.asuka.backend.pojo.entity.AnalysisTask;

/**
 * GitHub token 统一解析。
 *
 * <p>优先级：已绑定用户 token → 配置文件公用 token → 无 token。
 * 已绑定用户 token 失效时直接报错，不回退公用 token。
 */
public interface GitHubTokenProvider {

    GitHubAuth resolveForUser(Long userId);

    GitHubAuth resolveForTask(AnalysisTask task);

    /**
     * 401 时把用户绑定标记为失效；公用 token 不做降级。
     */
    void handleFailure(GitHubAuth auth, GitHubApiException ex);

    void markValidated(GitHubAuth auth);
}
