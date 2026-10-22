package com.asuka.backend.common.exception;

/**
 * GitHub 调用异常。
 *
 * <p>把 HTTP 状态和语义错误分开，方便分别映射成接口错误码和任务 errorCode。
 */
public class GitHubApiException extends RuntimeException {

    public enum Kind {
        NOT_FOUND,
        RATE_LIMITED,
        FORBIDDEN,
        SERVER_ERROR,
        TIMEOUT,
        UNKNOWN
    }

    private final Kind kind;

    private final int status;

    public GitHubApiException(Kind kind, int status, String message) {
        super(message);
        this.kind = kind;
        this.status = status;
    }

    public GitHubApiException(Kind kind, int status, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.status = status;
    }

    public Kind getKind() {
        return kind;
    }

    public int getStatus() {
        return status;
    }
}
