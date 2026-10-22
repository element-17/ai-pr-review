package com.asuka.backend.service.ai;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

/**
 * agent 终止工具调用结果。
 */
@Data
@AllArgsConstructor
public class AgentResult {

    private String terminalTool;

    private Map<String, Object> arguments;

    private int turns;

    private int toolCalls;
}
