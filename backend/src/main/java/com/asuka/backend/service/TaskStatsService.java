package com.asuka.backend.service;

import com.asuka.backend.pojo.vo.CoverageVO;
import com.asuka.backend.pojo.vo.FileStatsVO;
import com.asuka.backend.pojo.vo.IssueStatsVO;
import com.asuka.backend.pojo.vo.TaskStatsVO;

/**
 * 任务统计聚合服务。
 */
public interface TaskStatsService {

    TaskStatsVO buildTaskStats(Long taskId);

    FileStatsVO buildFileStats(Long taskId);

    IssueStatsVO buildIssueStats(Long taskId);

    CoverageVO buildCoverage(Long taskId);
}
