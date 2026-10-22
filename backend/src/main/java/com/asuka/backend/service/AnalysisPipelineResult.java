package com.asuka.backend.service;

/**
 * 分析 pipeline 执行结果。
 */
public record AnalysisPipelineResult(boolean partial, Integer errorCode, String errorMessage) {

    public static AnalysisPipelineResult completed() {
        return new AnalysisPipelineResult(false, null, null);
    }

    public static AnalysisPipelineResult partial(int errorCode, String errorMessage) {
        return new AnalysisPipelineResult(true, errorCode, errorMessage);
    }
}
