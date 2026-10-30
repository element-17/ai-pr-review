package com.asuka.backend.service.impl;

import com.asuka.backend.common.exception.GitHubApiException;
import com.asuka.backend.common.properties.GitHubProperties;
import com.asuka.backend.core.github.GitHubAuth;
import com.asuka.backend.pojo.entity.AnalysisTask;
import com.asuka.backend.service.GitHubBindingService;
import com.asuka.backend.service.GitHubTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * GitHub token 解析实现。
 *
 * <p>已绑定用户 token 失效时抛出 403 并携带 githubAuthRequired，
 * 不允许静默回退到公用 token。公用 token 未配置时降级为无 token 请求。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubTokenProviderImpl implements GitHubTokenProvider {

    private final GitHubProperties githubProperties;
    private final GitHubBindingService bindingService;

    @Override
    public GitHubAuth resolveForUser(Long userId) {
        if (userId != null) {
            String userToken = bindingService.getActiveAccessTokenOrNull(userId);
            if (StringUtils.hasText(userToken)) {
                return GitHubAuth.user(userToken, userId);
            }
        }
        String sharedToken = sharedTokenOrNull();
        return StringUtils.hasText(sharedToken)
                ? GitHubAuth.shared(sharedToken)
                : GitHubAuth.none();
    }

    @Override
    public GitHubAuth resolveForTask(AnalysisTask task) {
        return resolveForUser(task == null ? null : task.getUserId());
    }

    @Override
    public void handleFailure(GitHubAuth auth, GitHubApiException ex) {
        if (auth == null || ex == null) {
            return;
        }
        if (ex.getKind() == GitHubApiException.Kind.UNAUTHORIZED
                && auth.getSource() == GitHubAuth.Source.USER_BOUND
                && auth.getUserId() != null) {
            bindingService.markInvalid(auth.getUserId());
        }
    }

    @Override
    public void markValidated(GitHubAuth auth) {
        if (auth != null && auth.getSource() == GitHubAuth.Source.USER_BOUND
                && auth.getUserId() != null) {
            bindingService.markValidated(auth.getUserId());
        }
    }

    private String sharedTokenOrNull() {
        String token = githubProperties.getToken();
        if (!StringUtils.hasText(token)) {
            return null;
        }
        String normalized = token.trim();
        // 示例配置里的占位符不应被当成真实 token 发给 GitHub
        if (normalized.startsWith("your_") || normalized.startsWith("replace-")) {
            return null;
        }
        return normalized;
    }
}
