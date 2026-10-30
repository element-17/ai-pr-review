package com.asuka.backend.service.impl;

import com.asuka.backend.common.cache.CacheNames;
import com.asuka.backend.common.cache.LogicalCacheService;
import com.asuka.backend.common.constant.ReportLanguageConstant;
import com.asuka.backend.common.constant.ReviewIssueConstant;
import com.asuka.backend.common.constant.TaskFileConstant;
import com.asuka.backend.common.constant.TaskStatusConstant;
import com.asuka.backend.common.exception.BusinessException;
import com.asuka.backend.pojo.dto.IssueExportRow;
import com.asuka.backend.pojo.entity.AnalysisReport;
import com.asuka.backend.pojo.entity.AnalysisTask;
import com.asuka.backend.pojo.entity.PullRequest;
import com.asuka.backend.pojo.entity.TaskFile;
import com.asuka.backend.mapper.AnalysisReportMapper;
import com.asuka.backend.mapper.PullRequestMapper;
import com.asuka.backend.mapper.ReviewIssueMapper;
import com.asuka.backend.mapper.TaskFileMapper;
import com.asuka.backend.core.analysis.AnalysisConfigParser;
import com.asuka.backend.service.TaskAccessService;
import com.asuka.backend.service.TaskReportService;
import com.asuka.backend.service.TaskStatsService;
import com.asuka.backend.pojo.vo.AnalysisConfigVO;
import com.asuka.backend.pojo.vo.IssueStatsVO;
import com.asuka.backend.pojo.vo.MarkdownExportVO;
import com.asuka.backend.pojo.vo.ReportVO;
import com.asuka.backend.pojo.vo.TaskStatsVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 分析报告服务实现。
 *
 * <p>Markdown 导出完全按任务固定的 report_language 生成，
 * 不读取当前界面语言，避免切换界面语言时改写历史报告。
 */
@Service
@RequiredArgsConstructor
public class TaskReportServiceImpl implements TaskReportService {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AnalysisReportMapper analysisReportMapper;
    private final PullRequestMapper pullRequestMapper;
    private final TaskFileMapper taskFileMapper;
    private final ReviewIssueMapper reviewIssueMapper;
    private final TaskAccessService taskAccessService;
    private final TaskStatsService taskStatsService;
    private final AnalysisConfigParser analysisConfigParser;
    private final LogicalCacheService logicalCacheService;

    @Override
    public ReportVO getReport(Long taskId) {
        AnalysisTask task = taskAccessService.requireAccessibleTask(taskId);
        ReportVO vo = TaskStatusConstant.isFinished(task.getStatus())
                ? logicalCacheService.get(CacheNames.TASK_REPORT, String.valueOf(taskId),
                        CacheNames.TASK_REPORT_LOGICAL_TTL_SECONDS, () -> buildReport(taskId))
                : buildReport(taskId);
        if (vo == null) {
            throw new BusinessException(404, "报告尚未生成");
        }
        return vo;
    }

    private ReportVO buildReport(Long taskId) {
        AnalysisTask task = taskAccessService.requireTask(taskId);
        AnalysisReport report = analysisReportMapper.selectOne(
                new LambdaQueryWrapper<AnalysisReport>().eq(AnalysisReport::getTaskId, taskId));
        if (report == null) {
            // 失败、取消或尚未进入总结阶段的任务没有报告行；返回 null 以便缓存空结果
            return null;
        }

        ReportVO vo = new ReportVO();
        vo.setTaskId(taskId);
        vo.setReportLanguage(task.getReportLanguage());
        vo.setModelName(task.getModelName());
        AnalysisConfigVO config = analysisConfigParser.parse(task.getAnalysisConfig(), taskId);
        vo.setAnalysisConfig(config);
        if (config != null && config.getModel() != null) {
            vo.setModelDisplayName(config.getModel().getDisplayName());
            vo.setModelProvider(config.getModel().getProvider());
        } else {
            vo.setModelDisplayName(task.getModelName());
        }
        vo.setPurpose(report.getPurpose());
        vo.setChanges(report.getChanges());
        vo.setImpacts(report.getImpacts());
        vo.setCoverage(taskStatsService.buildCoverage(taskId));
        vo.setGeneratedAt(report.getUpdateTime() != null ? report.getUpdateTime() : task.getFinishTime());
        return vo;
    }

    @Override
    public MarkdownExportVO exportMarkdown(Long taskId, String format) {
        if (StringUtils.hasText(format) && !"markdown".equalsIgnoreCase(format.trim())) {
            throw new BusinessException(400, "当前只支持 markdown 导出");
        }

        AnalysisTask task = taskAccessService.requireAccessibleTask(taskId);
        AnalysisReport report = analysisReportMapper.selectOne(
                new LambdaQueryWrapper<AnalysisReport>().eq(AnalysisReport::getTaskId, taskId));
        if (report == null) {
            throw new BusinessException(404, "报告尚未生成");
        }

        PullRequest pr = pullRequestMapper.selectById(task.getPrId());
        TaskStatsVO stats = taskStatsService.buildTaskStats(taskId);
        List<TaskFile> skippedFiles = taskFileMapper.selectList(new LambdaQueryWrapper<TaskFile>()
                .select(TaskFile::getId, TaskFile::getFilePath, TaskFile::getSkipReason)
                .eq(TaskFile::getTaskId, taskId)
                .eq(TaskFile::getStatus, TaskFileConstant.STATUS_SKIPPED)
                .orderByAsc(TaskFile::getFilePath));
        List<IssueExportRow> issues = reviewIssueMapper.selectForExport(taskId);

        boolean en = ReportLanguageConstant.EN_US.equals(task.getReportLanguage());
        AnalysisConfigVO config = analysisConfigParser.parse(task.getAnalysisConfig(), taskId);
        String markdown = buildMarkdown(task, pr, report, stats, skippedFiles, issues, config, en);
        String filename = pr != null && pr.getPrNumber() != null
                ? "pr-" + pr.getPrNumber() + "-review-" + taskId + ".md"
                : "review-" + taskId + ".md";
        return new MarkdownExportVO(filename, markdown);
    }

    private String buildMarkdown(AnalysisTask task, PullRequest pr, AnalysisReport report,
                                 TaskStatsVO stats, List<TaskFile> skippedFiles,
                                 List<IssueExportRow> issues, AnalysisConfigVO config, boolean en) {
        StringBuilder md = new StringBuilder(8192);
        md.append("# ").append(en ? "PR Review Report" : "PR 评审报告").append("\n\n");

        appendPrInformation(md, task, pr, config, en);
        appendSummary(md, report, en);
        appendCoverage(md, stats, skippedFiles, en);
        appendFindings(md, issues, en);
        appendFeedbackSummary(md, stats.getIssues(), en);
        appendDisclaimer(md, en);
        return md.toString();
    }

    private void appendPrInformation(StringBuilder md, AnalysisTask task, PullRequest pr,
                                     AnalysisConfigVO config, boolean en) {
        md.append("## 1. ").append(en ? "PR Information" : "PR 信息").append("\n\n");
        md.append("- ").append(en ? "Repository: " : "仓库：")
                .append(pr == null ? "-" : value(pr.getRepoFullName())).append('\n');
        md.append("- ").append(en ? "PR: " : "PR 编号：")
                .append(pr == null ? "-" : "#" + pr.getPrNumber()).append('\n');
        md.append("- ").append(en ? "Title: " : "标题：")
                .append(pr == null ? "-" : value(pr.getTitle())).append('\n');
        md.append("- ").append(en ? "Author: " : "作者：")
                .append(pr == null ? "-" : value(pr.getAuthor())).append('\n');
        md.append("- ").append(en ? "Branches: " : "分支：")
                .append(pr == null ? "-" : value(pr.getBaseBranch()) + " <- " + value(pr.getHeadBranch()))
                .append('\n');
        md.append("- ").append(en ? "Commit: " : "提交版本：")
                .append('`').append(value(task.getHeadSha())).append("`\n");
        md.append("- ").append(en ? "Task: " : "任务 ID：")
                .append('#').append(task.getId()).append('\n');
        md.append("- ").append(en ? "Task status: " : "任务状态：")
                .append(taskStatusLabel(task.getStatus(), en)).append('\n');
        md.append("- ").append(en ? "Created: " : "创建时间：")
                .append(formatTime(task.getCreateTime())).append('\n');
        md.append("- ").append(en ? "Finished: " : "完成时间：")
                .append(formatTime(task.getFinishTime())).append('\n');
        md.append("- ").append(en ? "Report language: " : "报告语言：")
                .append(value(task.getReportLanguage())).append('\n');
        md.append("- ").append(en ? "Model: " : "模型：")
                .append(modelText(task, config)).append('\n');
        md.append("- ").append(en ? "Analysis config: " : "分析配置：")
                .append(configText(config, en)).append('\n');

        // 部分完成或失败时把原因写在报告开头，避免读者把不完整结果当成完整结论
        if (StringUtils.hasText(task.getErrorMessage())) {
            md.append("- ").append(en ? "Reason: " : "任务说明：")
                    .append(task.getErrorMessage()).append('\n');
        }
        md.append('\n');
    }

    private void appendSummary(StringBuilder md, AnalysisReport report, boolean en) {
        md.append("## 2. ").append(en ? "Review Summary" : "评审总结").append("\n\n");
        md.append("### ").append(en ? "Purpose" : "修改目的").append("\n\n")
                .append(report.getPurpose() == null ? notGenerated(en) : report.getPurpose()).append("\n\n");
        md.append("### ").append(en ? "Main Changes" : "主要变更").append("\n\n")
                .append(report.getChanges() == null ? notGenerated(en) : report.getChanges()).append("\n\n");
        md.append("### ").append(en ? "Impacted Modules" : "影响模块").append("\n\n")
                .append(report.getImpacts() == null ? notGenerated(en) : report.getImpacts()).append("\n\n");
    }

    private void appendCoverage(StringBuilder md, TaskStatsVO stats, List<TaskFile> skippedFiles, boolean en) {
        md.append("## 3. ").append(en ? "Coverage" : "覆盖范围").append("\n\n");
        md.append("- ").append(en ? "Total files: " : "文件总数：")
                .append(stats.getFiles().getTotal()).append('\n');
        md.append("- ").append(en ? "Analyzed: " : "已分析：")
                .append(stats.getFiles().getAnalyzed()).append('\n');
        md.append("- ").append(en ? "Skipped: " : "已跳过：")
                .append(stats.getFiles().getSkipped()).append('\n');
        md.append("- ").append(en ? "Failed: " : "分析失败：")
                .append(stats.getFiles().getFailed()).append("\n\n");

        md.append("### ").append(en ? "Skipped Files" : "跳过文件").append("\n\n");
        if (skippedFiles == null || skippedFiles.isEmpty()) {
            md.append(en ? "None." : "无。").append("\n\n");
            return;
        }
        for (TaskFile file : skippedFiles) {
            md.append("- `").append(value(file.getFilePath())).append("`: ")
                    .append(StringUtils.hasText(file.getSkipReason()) ? file.getSkipReason()
                            : (en ? "Reason not recorded" : "未记录原因"))
                    .append('\n');
        }
        md.append('\n');
    }

    private void appendFindings(StringBuilder md, List<IssueExportRow> issues, boolean en) {
        md.append("## 4. ").append(en ? "Findings" : "风险清单").append("\n\n");
        if (issues == null || issues.isEmpty()) {
            md.append(en ? "No candidate issues found." : "未发现候选问题。").append("\n\n");
            return;
        }

        int index = 1;
        for (IssueExportRow issue : issues) {
            md.append("### 4.").append(index).append(" [")
                    .append(severityLabel(issue.getSeverity(), en)).append("] ")
                    .append(value(issue.getTitle())).append("\n\n");
            md.append("- ").append(en ? "Category: " : "类别：")
                    .append(categoryLabel(issue.getCategory(), en)).append('\n');
            md.append("- ").append(en ? "Severity / Confidence: " : "严重度 / 置信度：")
                    .append(severityLabel(issue.getSeverity(), en)).append(" / ")
                    .append(issue.getConfidence() == null ? "-" : issue.getConfidence() + "%").append('\n');
            md.append("- ").append(en ? "Location: " : "定位：")
                    .append(locationText(issue, en)).append('\n');
            md.append("- ").append(en ? "Trigger: " : "触发条件：")
                    .append(value(issue.getTriggerCondition())).append('\n');
            md.append("- ").append(en ? "Impact: " : "潜在影响：")
                    .append(value(issue.getImpact())).append('\n');
            md.append("- ").append(en ? "Evidence: " : "证据：")
                    .append(StringUtils.hasText(issue.getEvidence())
                            ? issue.getEvidence()
                            : (en ? "Insufficient evidence; manual confirmation required."
                            : "证据不足，待人工确认。"))
                    .append('\n');
            md.append("- ").append(en ? "Suggestion: " : "建议：")
                    .append(value(issue.getSuggestion())).append('\n');
            md.append("- ").append(en ? "Status: " : "处理状态：")
                    .append(feedbackLabel(issue.getFeedbackStatus(), en))
                    .append(en ? "; Remark: " : "；理由：")
                    .append(StringUtils.hasText(issue.getFeedbackRemark()) ? issue.getFeedbackRemark()
                            : (en ? "None" : "无"))
                    .append("\n\n");
            index++;
        }
    }

    private void appendFeedbackSummary(StringBuilder md, IssueStatsVO stats, boolean en) {
        md.append("## 5. ").append(en ? "Feedback Summary" : "处理统计").append("\n\n");
        md.append("- ").append(en ? "Pending: " : "待确认：").append(stats.getPending()).append('\n');
        md.append("- ").append(en ? "Accepted: " : "已采纳：").append(stats.getAccepted()).append('\n');
        md.append("- ").append(en ? "Resolved: " : "已解决：").append(stats.getResolved()).append('\n');
        md.append("- ").append(en ? "False positive: " : "误报：").append(stats.getFalsePositive()).append('\n');
        md.append("- ").append(en ? "Severity distribution: " : "严重度分布：")
                .append(en ? "Low " : "低 ").append(stats.getBySeverity().getLow())
                .append(" / ").append(en ? "Medium " : "中 ").append(stats.getBySeverity().getMedium())
                .append(" / ").append(en ? "High " : "高 ").append(stats.getBySeverity().getHigh())
                .append(" / ").append(en ? "Critical " : "严重 ").append(stats.getBySeverity().getCritical())
                .append("\n\n");
    }

    private void appendDisclaimer(StringBuilder md, boolean en) {
        md.append("## 6. ").append(en ? "Disclaimer" : "免责声明").append("\n\n");
        md.append(en
                ? "This report is generated by AI. Findings are candidates and must be confirmed manually before "
                + "being used as a repair basis. Skipped or uncovered files do not mean the code is correct.\n"
                : "本报告由 AI 生成，结论为候选问题，需要人工确认后才能作为修复依据。跳过或未覆盖的文件不代表代码正确。\n");
    }

    private String modelText(AnalysisTask task, AnalysisConfigVO config) {
        if (config != null && config.getModel() != null) {
            String displayName = value(config.getModel().getDisplayName());
            String modelId = value(config.getModel().getModelId());
            return displayName.equals(modelId) ? displayName : displayName + " (" + modelId + ")";
        }
        return value(task.getModelName());
    }

    private String configText(AnalysisConfigVO config, boolean en) {
        if (config == null) {
            return en ? "Not recorded" : "未记录";
        }
        return (en ? "checkCategories=" : "检查类别=") + config.getCheckCategories()
                + (en ? ", ignoreDirs=" : "，忽略目录=") + config.getIgnoreDirs()
                + (en ? ", maxFileCount=" : "，最大文件数=") + config.getMaxFileCount()
                + (en ? ", maxDiffLines=" : "，最大 Diff 行数=") + config.getMaxDiffLines();
    }

    private String locationText(IssueExportRow issue, boolean en) {
        if (!StringUtils.hasText(issue.getFilePath())) {
            return en ? "Report-level finding" : "报告级提示";
        }
        StringBuilder location = new StringBuilder("`").append(issue.getFilePath()).append('`');
        if (issue.getStartLine() != null) {
            location.append(" L").append(issue.getStartLine());
            if (issue.getEndLine() != null && issue.getEndLine() > issue.getStartLine()) {
                location.append("-L").append(issue.getEndLine());
            }
        }
        return location.toString();
    }

    private String taskStatusLabel(Integer status, boolean en) {
        if (status == null) {
            return "-";
        }
        return switch (status) {
            case TaskStatusConstant.QUEUED -> en ? "Queued" : "排队中";
            case TaskStatusConstant.RUNNING -> en ? "Running" : "分析中";
            case TaskStatusConstant.COMPLETED -> en ? "Completed" : "已完成";
            case TaskStatusConstant.PARTIAL -> en ? "Partially completed" : "部分完成";
            case TaskStatusConstant.FAILED -> en ? "Failed" : "失败";
            case TaskStatusConstant.CANCELED -> en ? "Canceled" : "已取消";
            default -> "-";
        };
    }

    private String categoryLabel(Integer category, boolean en) {
        if (category == null) {
            return "-";
        }
        return switch (category) {
            case ReviewIssueConstant.CATEGORY_LOGIC -> en ? "Logic error" : "逻辑错误";
            case ReviewIssueConstant.CATEGORY_BOUNDARY -> en ? "Null and boundary" : "空值与边界";
            case ReviewIssueConstant.CATEGORY_EXCEPTION -> en ? "Exception handling" : "异常处理";
            case ReviewIssueConstant.CATEGORY_SECURITY -> en ? "Security and sensitive data" : "权限与敏感信息";
            case ReviewIssueConstant.CATEGORY_COMPATIBILITY -> en ? "API and data compatibility" : "接口与数据兼容";
            case ReviewIssueConstant.CATEGORY_TEST -> en ? "Missing tests" : "缺失测试";
            default -> en ? "Other" : "其他";
        };
    }

    private String severityLabel(Integer severity, boolean en) {
        if (severity == null) {
            return "-";
        }
        return switch (severity) {
            case ReviewIssueConstant.SEVERITY_LOW -> en ? "Low" : "低";
            case ReviewIssueConstant.SEVERITY_MEDIUM -> en ? "Medium" : "中";
            case ReviewIssueConstant.SEVERITY_HIGH -> en ? "High" : "高";
            case ReviewIssueConstant.SEVERITY_CRITICAL -> en ? "Critical" : "严重";
            default -> "-";
        };
    }

    private String feedbackLabel(Integer status, boolean en) {
        if (status == null) {
            return "-";
        }
        return switch (status) {
            case ReviewIssueConstant.FEEDBACK_ACCEPTED -> en ? "Accepted" : "已采纳";
            case ReviewIssueConstant.FEEDBACK_RESOLVED -> en ? "Resolved" : "已解决";
            case ReviewIssueConstant.FEEDBACK_FALSE_POSITIVE -> en ? "False positive" : "误报";
            default -> en ? "Pending" : "待确认";
        };
    }

    private String notGenerated(boolean en) {
        return en ? "Not generated" : "未生成";
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? "-" : TIME_FORMATTER.format(time);
    }

    private String value(String text) {
        return StringUtils.hasText(text) ? text : "-";
    }
}
