package com.asuka.backend.service.ai;

import com.asuka.backend.common.constant.ReportLanguageConstant;
import com.asuka.backend.common.constant.TaskErrorCodeConstant;
import com.asuka.backend.common.constant.TaskFileConstant;
import com.asuka.backend.common.constant.TaskStatusConstant;
import com.asuka.backend.common.util.PatchLineIndex;
import com.asuka.backend.config.AiProperties;
import com.asuka.backend.entity.AnalysisReport;
import com.asuka.backend.entity.AnalysisTask;
import com.asuka.backend.entity.PullRequest;
import com.asuka.backend.entity.TaskFile;
import com.asuka.backend.integration.ai.ModelException;
import com.asuka.backend.integration.github.GitHubApiException;
import com.asuka.backend.integration.github.GitHubClient;
import com.asuka.backend.integration.github.dto.GitHubCommitResponse;
import com.asuka.backend.integration.github.dto.GitHubPrResponse;
import com.asuka.backend.mapper.AnalysisReportMapper;
import com.asuka.backend.mapper.AnalysisTaskMapper;
import com.asuka.backend.mapper.TaskFileMapper;
import com.asuka.backend.service.AnalysisPipeline;
import com.asuka.backend.service.AnalysisPipelineResult;
import com.asuka.backend.service.TaskCanceledException;
import com.asuka.backend.vo.AnalysisConfigVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DeepSeek agent 分析 pipeline。
 *
 * <p>阶段 1 生成 PR 总结和重点文件；阶段 2 按 3-5 个文件一批提取候选问题。
 * 每批 agent 可以使用只读工具补上下文，最终通过 submit 工具提交结构化结果。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pr.ai.stub-pipeline", havingValue = "false", matchIfMissing = true)
public class DeepSeekAnalysisPipeline implements AnalysisPipeline {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AiProperties aiProperties;
    private final GitHubClient gitHubClient;
    private final TaskFileMapper taskFileMapper;
    private final AnalysisReportMapper analysisReportMapper;
    private final AnalysisTaskMapper analysisTaskMapper;
    private final AgentExecutor agentExecutor;
    private final PromptBuilder promptBuilder;
    private final IssueValidationService issueValidationService;

    @Override
    public AnalysisPipelineResult run(AnalysisTask task, PullRequest pr, AnalysisConfigVO config) {
        String[] repo = pr.getRepoFullName().split("/", 2);
        if (repo.length != 2) {
            throw new AnalysisPipelineException(TaskErrorCodeConstant.INTERNAL_ERROR, "仓库全名格式错误");
        }

        List<TaskFile> files = taskFileMapper.selectList(new LambdaQueryWrapper<TaskFile>()
                .eq(TaskFile::getTaskId, task.getId()));
        TaskContext taskContext = new TaskContext(task.getId(), repo[0], repo[1],
                task.getHeadSha(), files, new TaskContextCache());
        ContextBuilder contextBuilder = new ContextBuilder(gitHubClient, taskContext);
        boolean english = ReportLanguageConstant.EN_US.equals(task.getReportLanguage());

        String prBody = null;
        try {
            GitHubPrResponse response = gitHubClient.getPullRequest(repo[0], repo[1], pr.getPrNumber());
            prBody = response.getBody();
        } catch (GitHubApiException ex) {
            log.debug("读取 PR 描述失败，使用标题和 commit 信息：{}#{}", pr.getRepoFullName(), pr.getPrNumber());
        }
        List<GitHubCommitResponse> commits = List.of();
        try {
            commits = gitHubClient.listPullRequestCommits(repo[0], repo[1], pr.getPrNumber(), 100);
        } catch (GitHubApiException ex) {
            log.debug("读取 PR commit 信息失败：{}#{}", pr.getRepoFullName(), pr.getPrNumber());
        }

        List<Map<String, Object>> summaryFiles = files.stream()
                .filter(file -> file.getStatus() == null
                        || file.getStatus() != TaskFileConstant.STATUS_SKIPPED)
                .map(this::fileMeta)
                .toList();
        AgentRunContext summaryContext = new AgentRunContext(
                "summary", taskContext, contextBuilder, summaryFiles, english);

        AgentSummary summary;
        try {
            AgentResult result = agentExecutor.run(
                    summaryContext,
                    promptBuilder.summarySystem(english),
                    promptBuilder.summaryUser(task, pr, prBody, commits, files, config, english),
                    AgentToolDefinitions.summaryTools(),
                    Set.of("submit_summary"));
            summary = parse(result.getArguments(), AgentSummary.class);
            if (summary == null || (!hasText(summary.getPurpose())
                    && !hasText(summary.getChanges()) && !hasText(summary.getImpacts()))) {
                throw new AnalysisPipelineException(TaskErrorCodeConstant.MODEL_FORMAT_ERROR,
                        "PR 总结内容为空");
            }
        } catch (ModelException ex) {
            throw ex;
        } catch (AgentException ex) {
            throw new AnalysisPipelineException(TaskErrorCodeConstant.MODEL_FORMAT_ERROR,
                    "PR 总结生成失败");
        }

        saveSummary(task.getId(), summary, files);
        AnalysisReport report = findReport(task.getId());

        List<TaskFile> analyzable = files.stream()
                .filter(file -> file.getStatus() != null
                        && file.getStatus() == TaskFileConstant.STATUS_PENDING
                        && StringUtils.hasText(file.getPatch()))
                .toList();
        if (analyzable.isEmpty()) {
            return AnalysisPipelineResult.completed();
        }

        int batchSize = Math.max(3, Math.min(5, aiProperties.getBatchSize()));
        boolean partial = false;
        Integer errorCode = null;
        String errorMessage = null;

        for (int start = 0; start < analyzable.size(); start += batchSize) {
            checkCanceled(task.getId());
            List<TaskFile> batch = analyzable.subList(start,
                    Math.min(start + batchSize, analyzable.size()));
            List<Map<String, Object>> batchMeta = batch.stream().map(this::fileMeta).toList();
            AgentRunContext issueContext = new AgentRunContext(
                    "issues", taskContext, contextBuilder, batchMeta, english);

            try {
                AgentResult result = agentExecutor.run(
                        issueContext,
                        promptBuilder.issuesSystem(english),
                        promptBuilder.issuesUser(task, pr, report, batch, contextBuilder, config, english),
                        AgentToolDefinitions.issueTools(),
                        Set.of("submit_issues"));
                AgentIssues issues = parse(result.getArguments(), AgentIssues.class);
                if (issues == null) {
                    throw new AgentException("submit_issues 参数为空");
                }
                ValidationResult validation = issueValidationService.save(task.getId(), issues.getIssues());
                markFiles(batch, TaskFileConstant.STATUS_ANALYZED, null);
                if (validation.dropped() > 0) {
                    partial = true;
                    errorCode = TaskErrorCodeConstant.MODEL_FORMAT_ERROR;
                    errorMessage = "部分模型输出未通过校验";
                }
            } catch (ModelException ex) {
                markFiles(batch, TaskFileConstant.STATUS_FAILED, "模型调用失败");
                partial = true;
                errorCode = ex.toTaskErrorCode();
                errorMessage = "模型调用失败";
                log.warn("任务 {} 批次模型调用失败：{}", task.getId(), ex.getKind());
            } catch (AgentException ex) {
                markFiles(batch, TaskFileConstant.STATUS_FAILED, "AI 输出未通过校验");
                partial = true;
                errorCode = TaskErrorCodeConstant.MODEL_FORMAT_ERROR;
                errorMessage = "AI 输出未通过校验";
                log.warn("任务 {} 批次 agent 输出无效：{}", task.getId(), ex.getMessage());
            }
        }

        return partial
                ? AnalysisPipelineResult.partial(errorCode, errorMessage)
                : AnalysisPipelineResult.completed();
    }

    private void saveSummary(Long taskId, AgentSummary summary, List<TaskFile> files) {
        AnalysisReport report = findReport(taskId);
        boolean insert = report == null;
        if (insert) {
            report = new AnalysisReport();
            report.setTaskId(taskId);
        }
        report.setPurpose(nullToNull(summary.getPurpose()));
        report.setChanges(nullToNull(summary.getChanges()));
        report.setImpacts(nullToNull(summary.getImpacts()));
        if (insert) {
            analysisReportMapper.insert(report);
        } else {
            analysisReportMapper.updateById(report);
        }

        if (summary.getFiles() == null) {
            return;
        }
        Map<String, TaskFile> byPath = new LinkedHashMap<>();
        for (TaskFile file : files) {
            byPath.put(file.getFilePath(), file);
        }
        LocalDateTime now = LocalDateTime.now();
        for (AgentSummary.FileSummary item : summary.getFiles()) {
            if (item == null || !StringUtils.hasText(item.getPath())
                    || !byPath.containsKey(item.getPath())) {
                continue;
            }
            taskFileMapper.update(null, new LambdaUpdateWrapper<TaskFile>()
                    .eq(TaskFile::getTaskId, taskId)
                    .eq(TaskFile::getFilePath, item.getPath())
                    .set(TaskFile::getSummary, nullToNull(item.getSummary()))
                    .set(TaskFile::getPriorityReason, nullToNull(item.getPriorityReason()))
                    .set(TaskFile::getUpdateTime, now));
        }
    }

    private AnalysisReport findReport(Long taskId) {
        return analysisReportMapper.selectOne(new LambdaQueryWrapper<AnalysisReport>()
                .eq(AnalysisReport::getTaskId, taskId)
                .last("LIMIT 1"));
    }

    private void markFiles(List<TaskFile> files, int status, String reason) {
        LocalDateTime now = LocalDateTime.now();
        for (TaskFile file : files) {
            taskFileMapper.update(null, new LambdaUpdateWrapper<TaskFile>()
                    .eq(TaskFile::getId, file.getId())
                    .set(TaskFile::getStatus, status)
                    .set(TaskFile::getSkipReason, reason)
                    .set(TaskFile::getUpdateTime, now));
        }
    }

    private Map<String, Object> fileMeta(TaskFile file) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("path", file.getFilePath());
        meta.put("firstAddedLine", firstAddedLine(file));
        return meta;
    }

    private Integer firstAddedLine(TaskFile file) {
        Set<Integer> added = PatchLineIndex.parse(file.getPatch()).getAddedLines();
        return added.stream().min(Integer::compareTo).orElse(null);
    }

    private void checkCanceled(Long taskId) {
        AnalysisTask current = analysisTaskMapper.selectById(taskId);
        if (current == null || current.getStatus() == null
                || current.getStatus() != TaskStatusConstant.RUNNING) {
            throw new TaskCanceledException();
        }
    }

    private <T> T parse(Map<String, Object> arguments, Class<T> type) {
        try {
            return OBJECT_MAPPER.convertValue(arguments, type);
        } catch (Exception ex) {
            throw new AnalysisPipelineException(TaskErrorCodeConstant.MODEL_FORMAT_ERROR,
                    "模型输出格式错误");
        }
    }

    private String nullToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return StringUtils.hasText(value);
    }
}
