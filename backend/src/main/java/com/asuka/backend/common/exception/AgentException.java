package com.asuka.backend.common.exception;

/**
 * agent 工具循环失败。
 */
public class AgentException extends RuntimeException {

    public AgentException(String message) {
        super(message);
    }
}
