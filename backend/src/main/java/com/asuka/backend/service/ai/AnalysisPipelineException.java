package com.asuka.backend.service.ai;

/**
 * 分析 pipeline 的致命错误，worker 会按 errorCode 标记任务失败。
 */
public class AnalysisPipelineException extends RuntimeException {

    private final int errorCode;

    public AnalysisPipelineException(int errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
