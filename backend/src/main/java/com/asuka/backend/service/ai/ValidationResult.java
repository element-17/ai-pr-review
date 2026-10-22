package com.asuka.backend.service.ai;

/**
 * 问题校验结果。
 */
public record ValidationResult(int inserted, int dropped, int degraded) {
}
