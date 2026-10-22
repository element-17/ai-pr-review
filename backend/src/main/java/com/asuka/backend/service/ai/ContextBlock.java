package com.asuka.backend.service.ai;

/**
 * 函数级上下文块。
 */
public record ContextBlock(int startLine, int endLine, String content) {
}
