package com.asuka.backend.core.github;

import com.asuka.backend.common.exception.BusinessException;
import com.asuka.backend.common.exception.GitHubApiException;

/**
 * GitHub 异常到平台错误的统一映射。
 */
public final class GitHubErrorMapper {

    /**
     * 接口层错误：沿用当前骨架的 4xx/5xx 业务码。
     */
    public static BusinessException toBusinessException(GitHubApiException ex) {
        return switch (ex.getKind()) {
            case NOT_FOUND -> new BusinessException(404, "PR 不存在或无权访问");
            case RATE_LIMITED -> new BusinessException(429, "GitHub 请求过于频繁，请稍后重试");
            case FORBIDDEN -> new BusinessException(403, "无权访问该仓库");
            case TIMEOUT -> new BusinessException(504, "GitHub 请求超时");
            case SERVER_ERROR, UNKNOWN -> new BusinessException(502, "GitHub 请求失败");
        };
    }

    /**
     * 任务层错误码：1 PR 无效，2 限流，3 无权限，4 网络/服务端失败。
     */
    public static int toTaskErrorCode(GitHubApiException ex) {
        return switch (ex.getKind()) {
            case NOT_FOUND -> 1;
            case RATE_LIMITED -> 2;
            case FORBIDDEN -> 3;
            case SERVER_ERROR, TIMEOUT, UNKNOWN -> 4;
        };
    }

    private GitHubErrorMapper() {
    }
}
