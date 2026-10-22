package com.asuka.backend.analysis;

import com.asuka.backend.constant.ReportLanguageConstant;
import com.asuka.backend.constant.TaskErrorCodeConstant;
import com.asuka.backend.constant.TaskFileConstant;
import com.asuka.backend.constant.TaskStatusConstant;
import com.asuka.backend.utils.PatchLineIndex;
import com.asuka.backend.properties.AiProperties;
import com.asuka.backend.entity.AnalysisReport;
import com.asuka.backend.entity.AnalysisTask;
import com.asuka.backend.entity.PullRequest;
import com.asuka.backend.entity.TaskFile;
import com.asuka.backend.exception.AgentException;
import com.asuka.backend.exception.AnalysisPipelineException;
import com.asuka.backend.exception.ModelException;
import com.asuka.backend.exception.GitHubApiException;
import com.asuka.backend.integration.github.GitHubClient;
import com.asuka.backend.integration.github.dto.GitHubCommitResponse;
import com.asuka.backend.integration.github.dto.GitHubPrResponse;
import com.asuka.backend.mapper.AnalysisReportMapper;
import com.asuka.backend.mapper.AnalysisTaskMapper;
import com.asuka.backend.mapper.TaskFileMapper;
import com.asuka.backend.analysis.AnalysisPipeline;
import com.asuka.backend.analysis.AnalysisResult;
import com.asuka.backend.exception.TaskCanceledException;
import com.asuka.backend.vo.AnalysisConfigVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
    public AnalysisResult run(AnalysisTask task, PullRequest pr, AnalysisConfigVO config) {
        String[] repo = pr.getRepoFullName().split("/", 2);
        if (repo.length != 2) {
            throw new AnalysisPipelineException(TaskErrorCodeConstant.INTERNAL_ERROR, "仓库全名格式错误");
        }

        List<TaskFile> files = taskFileMapper.selectList(new LambdaQueryWrapper<TaskFile>()
                .eq(TaskFile::getTaskId, task.getId()));
        AnalysisContext taskContext = new AnalysisContext(task.getId(), repo[0], repo[1],
                task.getHeadSha(), files, new ContextFileCache());
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
        AgentContext summaryContext = new AgentContext(
                "summary", taskContext, contextBuilder, summaryFiles, english);

        checkCanceled(task.getId());
        SummaryDraft summary;
        try {
            AgentSubmission result = agentExecutor.run(
                    summaryContext,
                    promptBuilder.summarySystem(english),
                    promptBuilder.summaryUser(task, pr, prBody, commits, files, config, english),
                    AgentToolDefinitions.summaryTools(),
                    Set.of("submit_summary"));
            summary = parse(result.getArguments(), SummaryDraft.class);
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
            return AnalysisResult.completed();
        }

        int batchSize = Math.max(3, Math.min(5, aiProperties.getBatchSize()));
        int batchCount = (analyzable.size() + batchSize - 1) / batchSize;
        int concurrency = Math.max(1, Math.min(4, aiProperties.getBatchConcurrency()));
        log.info("任务 {} 问题分析分块：files={}, batchSize={}, batches={}, concurrency={}",
                task.getId(), analyzable.size(), batchSize, batchCount, concurrency);

        boolean partial = false;
        Integer errorCode = null;
        String errorMessage = null;

        String traceId = MDC.get("traceId");
        ExecutorService executor = Executors.newFixedThreadPool(concurrency, runnable -> {
            Thread thread = new Thread(() -> {
                if (traceId != null) {
                    MDC.put("traceId", traceId);
                }
                try {
                    runnable.run();
                } finally {
                    MDC.remove("traceId");
                }
            }, "ai-batch-" + task.getId());
            thread.setDaemon(true);
            return thread;
        });
        ExecutorCompletionService<BatchOutcome> completion = new ExecutorCompletionService<>(executor);
        List<Future<BatchOutcome>> futures = new ArrayList<>(batchCount);
        Object issueSaveLock = new Object();

        try {
            for (int start = 0; start < analyzable.size(); start += batchSize) {
                List<TaskFile> batch = List.copyOf(analyzable.subList(start,
                        Math.min(start + batchSize, analyzable.size())));
                futures.add(completion.submit(() -> analyzeBatch(
                        task, pr, report, taskContext, contextBuilder, english,
                        batch, config, issueSaveLock)));
            }

            for (int completed = 1; completed <= batchCount; completed++) {
                Future<BatchOutcome> future;
                try {
                    future = completion.take();
                    BatchOutcome outcome = future.get();
                    updateBatchProgress(task.getId(), completed, batchCount);
                    if (outcome.partial()) {
                        partial = true;
                        if (errorCode == null) {
                            errorCode = outcome.errorCode();
                            errorMessage = outcome.errorMessage();
                        }
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    futures.forEach(item -> item.cancel(true));
                    throw new TaskCanceledException();
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    if (cause instanceof TaskCanceledException canceled) {
                        futures.forEach(item -> item.cancel(true));
                        throw canceled;
                    }
                    log.error("任务 {} 批次执行异常", task.getId(), cause);
                    partial = true;
                    if (errorCode == null) {
                        errorCode = TaskErrorCodeConstant.INTERNAL_ERROR;
                        errorMessage = "批次执行异常";
                    }
                }
            }
        } finally {
            futures.forEach(item -> item.cancel(true));
            executor.shutdownNow();
        }

        return partial
                ? AnalysisResult.partial(errorCode, errorMessage)
                : AnalysisResult.completed();
    }

    /**
     * 单个文件批次：模型调用和工具循环在独立线程执行，校验入库串行化避免重复问题竞争。
     */
    private BatchOutcome analyzeBatch(AnalysisTask task,
                                      PullRequest pr,
                                      AnalysisReport report,
                                      AnalysisContext taskContext,
                                      ContextBuilder contextBuilder,
                                      boolean english,
                                      List<TaskFile> batch,
                                      AnalysisConfigVO config,
                                      Object issueSaveLock) {
        try {
            checkCanceled(task.getId());
            List<Map<String, Object>> batchMeta = batch.stream().map(this::fileMeta).toList();
            AgentContext issueContext = new AgentContext(
                    "issues", taskContext, contextBuilder, batchMeta, english);

            AgentSubmission result = agentExecutor.run(
                    issueContext,
                    promptBuilder.issuesSystem(english),
                    promptBuilder.issuesUser(task, pr, report, batch, contextBuilder, config, english),
                    AgentToolDefinitions.issueTools(),
                    Set.of("submit_issues"));
            checkCanceled(task.getId());
            IssueDraftList issues = parse(result.getArguments(), IssueDraftList.class);
            if (issues == null) {
                throw new AgentException("submit_issues 参数为空");
            }

            IssueValidationResult validation;
            synchronized (issueSaveLock) {
                validation = issueValidationService.save(task.getId(), issues.getIssues());
            }
            markFiles(batch, TaskFileConstant.STATUS_ANALYZED, null);
            if (validation.dropped() > 0) {
                log.warn("任务 {} 批次有 {} 条问题未通过校验", task.getId(), validation.dropped());
                return BatchOutcome.dropped();
            }
            log.info("任务 {} 批次完成：files={}, inserted={}", task.getId(),
                    batch.size(), validation.inserted());
            return BatchOutcome.completed();
        } catch (TaskCanceledException ex) {
            throw ex;
        } catch (ModelException ex) {
            markFiles(batch, TaskFileConstant.STATUS_FAILED, "模型调用失败");
            log.warn("任务 {} 批次模型调用失败：{}", task.getId(), ex.getKind());
            return BatchOutcome.failed(ex.toTaskErrorCode(), "模型调用失败");
        } catch (AgentException ex) {
            markFiles(batch, TaskFileConstant.STATUS_FAILED, "AI 输出未通过校验");
            log.warn("任务 {} 批次 agent 输出无效：{}", task.getId(), ex.getMessage());
            return BatchOutcome.failed(TaskErrorCodeConstant.MODEL_FORMAT_ERROR, "AI 输出未通过校验");
        } catch (AnalysisPipelineException ex) {
            markFiles(batch, TaskFileConstant.STATUS_FAILED, "模型输出格式错误");
            log.warn("任务 {} 批次模型输出格式错误：{}", task.getId(), ex.getMessage());
            return BatchOutcome.failed(ex.getErrorCode(), "模型输出格式错误");
        } catch (Exception ex) {
            markFiles(batch, TaskFileConstant.STATUS_FAILED, "批次执行异常");
            log.error("任务 {} 批次执行异常", task.getId(), ex);
            return BatchOutcome.failed(TaskErrorCodeConstant.INTERNAL_ERROR, "批次执行异常");
        }
    }

    private void updateBatchProgress(Long taskId, int completed, int total) {
        int progress = Math.min(89, 50 + (int) Math.round(40.0 * completed / total));
        int updated = analysisTaskMapper.update(null, new LambdaUpdateWrapper<AnalysisTask>()
                .eq(AnalysisTask::getId, taskId)
                .eq(AnalysisTask::getStatus, TaskStatusConstant.RUNNING)
                .set(AnalysisTask::getProgress, progress)
                .set(AnalysisTask::getUpdateTime, LocalDateTime.now()));
        if (updated == 0) {
            throw new TaskCanceledException();
        }
    }

    private record BatchOutcome(boolean partial, Integer errorCode, String errorMessage) {

        static BatchOutcome completed() {
            return new BatchOutcome(false, null, null);
        }

        static BatchOutcome dropped() {
            return new BatchOutcome(true, TaskErrorCodeConstant.MODEL_FORMAT_ERROR,
                    "部分模型输出未通过校验");
        }

        static BatchOutcome failed(Integer errorCode, String errorMessage) {
            return new BatchOutcome(true, errorCode, errorMessage);
        }
    }

    private void saveSummary(Long taskId, SummaryDraft summary, List<TaskFile> files) {
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
        for (SummaryDraft.FileSummary item : summary.getFiles()) {
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
