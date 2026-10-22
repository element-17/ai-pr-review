package com.asuka.backend.analysis;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 一次 agent 运行上下文。
 */
@Data
@AllArgsConstructor
public class AgentContext {

    private String stage;

    private AnalysisContext taskContext;

    private ContextBuilder contextBuilder;

    /** 每项包含 path、firstAddedLine，供 fake client 使用 */
    private List<Map<String, Object>> files;

    private boolean english;
}
