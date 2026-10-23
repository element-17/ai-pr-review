package com.asuka.backend.common.exception;

/**
 * 业务异常。
 *
 * <p>业务代码中主动抛出该异常，由 {@link GlobalExceptionHandler} 统一转换为
 * {@code Result}，避免在 Controller 中写大量 if-else 错误分支。
 */
public class BusinessException extends RuntimeException {

    private final Integer code;

    public BusinessException(String message) {
        this(500, message);
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }
}
