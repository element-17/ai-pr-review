package com.asuka.backend.common.exception;

/**
 * GitHub OAuth 调用异常。
 *
 * <p>只保存可安全记录的原因分类，不保存授权码、access token 或 GitHub 原始响应。
 */
public class GitHubOAuthException extends RuntimeException {

    public enum Reason {
        NOT_CONFIGURED,
        TOKEN_EXCHANGE_FAILED,
        USER_FETCH_FAILED,
        EMAIL_FETCH_FAILED,
        REVOKE_FAILED
    }

    private final Reason reason;

    public GitHubOAuthException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public GitHubOAuthException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
