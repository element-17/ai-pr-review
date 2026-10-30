package com.asuka.backend.core.github;

/**
 * 一次 GitHub 请求使用的认证信息。
 *
 * <p>token 可能为空；toString 永远不输出 token 明文，避免误打日志。
 */
public final class GitHubAuth {

    public enum Source {
        /** 已绑定用户自己的 token */
        USER_BOUND,
        /** 配置文件中的公用 token */
        SHARED,
        /** 未认证请求，受 GitHub 60 次/小时限制 */
        NONE
    }

    private final String token;

    private final Source source;

    private final Long userId;

    private GitHubAuth(String token, Source source, Long userId) {
        this.token = token;
        this.source = source;
        this.userId = userId;
    }

    public static GitHubAuth user(String token, Long userId) {
        return new GitHubAuth(token, Source.USER_BOUND, userId);
    }

    public static GitHubAuth shared(String token) {
        return new GitHubAuth(token, Source.SHARED, null);
    }

    public static GitHubAuth none() {
        return new GitHubAuth(null, Source.NONE, null);
    }

    public String getToken() {
        return token;
    }

    public Source getSource() {
        return source;
    }

    public Long getUserId() {
        return userId;
    }

    public boolean hasToken() {
        return token != null && !token.isBlank();
    }

    @Override
    public String toString() {
        return "GitHubAuth{source=" + source
                + ", userId=" + userId
                + ", token=" + (hasToken() ? "***" : "-") + "}";
    }
}
