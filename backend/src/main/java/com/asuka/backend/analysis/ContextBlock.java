package com.asuka.backend.analysis;

/**
 * 函数级上下文块。
 */
public record ContextBlock(int startLine, int endLine, String content) {
}
