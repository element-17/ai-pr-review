package com.asuka.backend.core.analysis;

import com.asuka.backend.pojo.entity.TaskFile;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 一次任务分析上下文。
 */
@Data
@AllArgsConstructor
public class AnalysisContext {

    private Long taskId;

    private String owner;

    private String repo;

    private String headSha;

    private List<TaskFile> files;

    private ContextFileCache cache;
}
