package com.asuka.backend.core.github;

import com.asuka.backend.common.exception.BusinessException;
import com.asuka.backend.common.exception.GitHubApiException;

import java.util.Map;

/**
 * GitHub 异常到平台错误的统一映射。
 *
 * <p>用户 token 失效用 403 + githubAuthRequired 表达，不能使用 401，
 * 否则前端会误判为平台会话过期并触发 refresh/登出。
 */
public final class GitHubErrorMapper {

    private static final Map<String, Object> REAUTHORIZE_DATA = Map.of(
            "githubAuthRequired", true,
            "action", "REAUTHORIZE"
    );

    public static BusinessException toBusinessException(GitHubApiException ex, GitHubAuth auth) {
        GitHubAuth.Source source = auth == null ? GitHubAuth.Source.NONE : auth.getSource();
        return switch (ex.getKind()) {
            case UNAUTHORIZED -> switch (source) {
                case USER_BOUND -> new BusinessException(403,
                        "GitHub 授权已失效，请重新授权", REAUTHORIZE_DATA);
                case SHARED -> new BusinessException(502,
                        "服务端 GitHub Token 配置无效，请联系管理员");
                case NONE -> new BusinessException(502, "GitHub 认证失败");
            };
            case NOT_FOUND -> new BusinessException(404, "PR 不存在或无权访问");
            case RATE_LIMITED -> source == GitHubAuth.Source.NONE
                    ? new BusinessException(429,
                    "GitHub 未认证请求已限流，请稍后重试或配置服务端 GitHub Token")
                    : new BusinessException(429, "GitHub 请求过于频繁，请稍后重试");
            case FORBIDDEN -> new BusinessException(403, "无权访问该仓库");
            case TIMEOUT -> new BusinessException(504, "GitHub 请求超时");
            case SERVER_ERROR, UNKNOWN -> new BusinessException(502, "GitHub 请求失败");
        };
    }

    /**
     * 兼容旧调用：没有 token 上下文时按无认证请求处理。
     */
    public static BusinessException toBusinessException(GitHubApiException ex) {
        return toBusinessException(ex, GitHubAuth.none());
    }

    public static int toTaskErrorCode(GitHubApiException ex, GitHubAuth auth) {
        GitHubAuth.Source source = auth == null ? GitHubAuth.Source.NONE : auth.getSource();
        return switch (ex.getKind()) {
            case UNAUTHORIZED -> source == GitHubAuth.Source.USER_BOUND ? 10 : 11;
            case NOT_FOUND -> 1;
            case RATE_LIMITED -> 2;
            case FORBIDDEN -> 3;
            case SERVER_ERROR, TIMEOUT, UNKNOWN -> 4;
        };
    }

    /**
     * 兼容旧调用：没有 token 上下文时按无认证请求处理。
     */
    public static int toTaskErrorCode(GitHubApiException ex) {
        return toTaskErrorCode(ex, GitHubAuth.none());
    }

    public static String toTaskErrorMessage(GitHubApiException ex, GitHubAuth auth) {
        GitHubAuth.Source source = auth == null ? GitHubAuth.Source.NONE : auth.getSource();
        return switch (ex.getKind()) {
            case UNAUTHORIZED -> source == GitHubAuth.Source.USER_BOUND
                    ? "GitHub 授权已失效，请重新授权"
                    : "服务端 GitHub Token 配置无效";
            case NOT_FOUND -> "PR 不存在或无权访问";
            case RATE_LIMITED -> "GitHub 请求过于频繁";
            case FORBIDDEN -> "无权访问该仓库";
            case TIMEOUT -> "GitHub 请求超时";
            case SERVER_ERROR, UNKNOWN -> "GitHub 请求失败";
        };
    }

    private GitHubErrorMapper() {
    }
}
