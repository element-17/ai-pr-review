package com.asuka.backend.service.ai;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 一次 agent 运行上下文。
 */
@Data
@AllArgsConstructor
public class AgentRunContext {

    private String stage;

    private TaskContext taskContext;

    private ContextBuilder contextBuilder;

    /** 每项包含 path、firstAddedLine，供 fake client 使用 */
    private List<Map<String, Object>> files;

    private boolean english;
}
