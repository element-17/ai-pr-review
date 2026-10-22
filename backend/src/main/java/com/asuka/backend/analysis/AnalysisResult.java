package com.asuka.backend.analysis;

/**
 * 分析 pipeline 执行结果。
 */
public record AnalysisResult(boolean partial, Integer errorCode, String errorMessage) {

    public static AnalysisResult completed() {
        return new AnalysisResult(false, null, null);
    }

    public static AnalysisResult partial(int errorCode, String errorMessage) {
        return new AnalysisResult(true, errorCode, errorMessage);
    }
}
