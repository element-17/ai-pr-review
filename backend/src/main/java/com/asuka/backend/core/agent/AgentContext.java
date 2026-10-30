package com.asuka.backend.core.agent;

import com.asuka.backend.core.agent.ai.ResolvedModel;
import com.asuka.backend.core.analysis.AnalysisContext;
import com.asuka.backend.core.analysis.ContextBuilder;
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

    /** 本次任务实际使用的模型 */
    private ResolvedModel model;
}
