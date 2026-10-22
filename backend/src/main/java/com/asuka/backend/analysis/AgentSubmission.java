package com.asuka.backend.analysis;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

/**
 * agent 终止工具调用结果。
 */
@Data
@AllArgsConstructor
public class AgentSubmission {

    private String terminalTool;

    private Map<String, Object> arguments;

    private int turns;

    private int toolCalls;
}
