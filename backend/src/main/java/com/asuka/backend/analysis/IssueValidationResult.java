package com.asuka.backend.analysis;

/**
 * 问题校验结果。
 */
public record IssueValidationResult(int inserted, int dropped, int degraded) {
}
