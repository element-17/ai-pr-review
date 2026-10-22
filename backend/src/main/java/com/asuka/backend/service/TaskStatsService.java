package com.asuka.backend.service;

import com.asuka.backend.constant.ReviewIssueConstant;
import com.asuka.backend.dto.StatusCountRow;
import com.asuka.backend.mapper.ReviewIssueMapper;
import com.asuka.backend.mapper.TaskFileMapper;
import com.asuka.backend.vo.CoverageVO;
import com.asuka.backend.vo.FileStatsVO;
import com.asuka.backend.vo.IssueStatsVO;
import com.asuka.backend.vo.SeverityStatsVO;
import com.asuka.backend.vo.TaskStatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 任务统计聚合。
 *
 * <p>工作台总览、问题列表和 Markdown 导出都复用这里的统计口径，
 * 避免不同接口对“已跳过”“误报”等状态给出不一致的数字。
 */
@Service
@RequiredArgsConstructor
public class TaskStatsService {

    private final TaskFileMapper taskFileMapper;
    private final ReviewIssueMapper reviewIssueMapper;

    /**
     * 文件覆盖 + 问题处理进度的完整统计。
     */
    public TaskStatsVO buildTaskStats(Long taskId) {
        TaskStatsVO stats = new TaskStatsVO();
        stats.setFiles(buildFileStats(taskId));
        stats.setIssues(buildIssueStats(taskId));
        return stats;
    }

    /**
     * 文件覆盖统计：按 task_file.status 分组。
     */
    public FileStatsVO buildFileStats(Long taskId) {
        List<StatusCountRow> rows = taskFileMapper.countByStatus(taskId);
        long total = 0;
        long analyzed = 0;
        long skipped = 0;
        long failed = 0;
        for (StatusCountRow row : rows) {
            long count = row.getTotal() == null ? 0 : row.getTotal();
            total += count;
            if (row.getStatus() == null) {
                continue;
            }
            switch (row.getStatus()) {
                case 2 -> analyzed = count;
                case 3 -> skipped = count;
                case 4 -> failed = count;
                default -> {
                    // 1 待分析不单独展示，但仍计入 total
                }
            }
        }
        FileStatsVO stats = new FileStatsVO();
        stats.setTotal(total);
        stats.setAnalyzed(analyzed);
        stats.setSkipped(skipped);
        stats.setFailed(failed);
        return stats;
    }

    /**
     * 问题统计：处理状态和严重度分开聚合。
     */
    public IssueStatsVO buildIssueStats(Long taskId) {
        List<StatusCountRow> feedbackRows = reviewIssueMapper.countByFeedbackStatus(taskId);
        List<StatusCountRow> severityRows = reviewIssueMapper.countBySeverity(taskId);

        long total = 0;
        long pending = 0;
        long accepted = 0;
        long resolved = 0;
        long falsePositive = 0;
        for (StatusCountRow row : feedbackRows) {
            long count = row.getTotal() == null ? 0 : row.getTotal();
            total += count;
            if (row.getStatus() == null) {
                continue;
            }
            switch (row.getStatus()) {
                case ReviewIssueConstant.FEEDBACK_ACCEPTED -> accepted = count;
                case ReviewIssueConstant.FEEDBACK_RESOLVED -> resolved = count;
                case ReviewIssueConstant.FEEDBACK_FALSE_POSITIVE -> falsePositive = count;
                default -> pending = count;
            }
        }

        long low = 0;
        long medium = 0;
        long high = 0;
        long critical = 0;
        for (StatusCountRow row : severityRows) {
            long count = row.getTotal() == null ? 0 : row.getTotal();
            if (row.getStatus() == null) {
                continue;
            }
            switch (row.getStatus()) {
                case ReviewIssueConstant.SEVERITY_MEDIUM -> medium = count;
                case ReviewIssueConstant.SEVERITY_HIGH -> high = count;
                case ReviewIssueConstant.SEVERITY_CRITICAL -> critical = count;
                default -> low = count;
            }
        }

        SeverityStatsVO bySeverity = new SeverityStatsVO();
        bySeverity.setLow(low);
        bySeverity.setMedium(medium);
        bySeverity.setHigh(high);
        bySeverity.setCritical(critical);

        IssueStatsVO stats = new IssueStatsVO();
        stats.setTotal(total);
        stats.setPending(pending);
        stats.setAccepted(accepted);
        stats.setResolved(resolved);
        stats.setFalsePositive(falsePositive);
        stats.setBySeverity(bySeverity);
        return stats;
    }

    /**
     * 报告覆盖范围：由文件统计转换，不额外存冗余计数。
     */
    public CoverageVO buildCoverage(Long taskId) {
        FileStatsVO fileStats = buildFileStats(taskId);
        CoverageVO coverage = new CoverageVO();
        coverage.setTotalFiles(fileStats.getTotal());
        coverage.setAnalyzedFiles(fileStats.getAnalyzed());
        coverage.setSkippedFiles(fileStats.getSkipped());
        coverage.setFailedFiles(fileStats.getFailed());
        return coverage;
    }
}
