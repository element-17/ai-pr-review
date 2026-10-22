package com.asuka.backend.exception;

/**
 * 模型调用异常。
 */
public class ModelException extends RuntimeException {

    public enum Kind {
        NOT_CONFIGURED,
        TIMEOUT,
        FORMAT,
        UPSTREAM
    }

    private final Kind kind;

    public ModelException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public ModelException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }

    /**
     * 映射到 analysis_task.error_code。
     */
    public int toTaskErrorCode() {
        return switch (kind) {
            case TIMEOUT -> 6;
            case FORMAT -> 7;
            default -> 8;
        };
    }
}
