package com.asuka.backend.service;

import com.asuka.backend.common.constant.TaskErrorCodeConstant;
import com.asuka.backend.common.constant.TaskFileConstant;
import com.asuka.backend.common.constant.TaskStatusConstant;
import com.asuka.backend.entity.AnalysisTask;
import com.asuka.backend.entity.PullRequest;
import com.asuka.backend.entity.TaskFile;
import com.asuka.backend.integration.ai.ModelException;
import com.asuka.backend.integration.github.GitHubApiException;
import com.asuka.backend.integration.github.GitHubErrorMapper;
import com.asuka.backend.service.ai.AnalysisPipelineException;
import com.asuka.backend.mapper.AnalysisTaskMapper;
import com.asuka.backend.mapper.PullRequestMapper;
import com.asuka.backend.mapper.TaskFileMapper;
import com.asuka.backend.vo.AnalysisConfigVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分析任务 worker。
 *
 * <p>状态流转由数据库条件更新保证：claim 1→2，进度和完成只更新 status=2 的任务；
 * 用户取消后条件更新返回 0，worker 在下一个阶段边界停止。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskWorker {

    private final AnalysisTaskMapper analysisTaskMapper;
    private final PullRequestMapper pullRequestMapper;
    private final TaskFileMapper taskFileMapper;
    private final TaskFileIngestionService taskFileIngestionService;
    private final AnalysisPipeline analysisPipeline;
    private final AnalysisConfigParser analysisConfigParser;

    public void process(Long taskId) {
        long started = System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now();
        int claimed = analysisTaskMapper.update(null, new LambdaUpdateWrapper<AnalysisTask>()
                .eq(AnalysisTask::getId, taskId)
                .eq(AnalysisTask::getStatus, TaskStatusConstant.QUEUED)
                .set(AnalysisTask::getStatus, TaskStatusConstant.RUNNING)
                .set(AnalysisTask::getStage, TaskStatusConstant.STAGE_FETCH_PR)
                .set(AnalysisTask::getProgress, 5)
                .set(AnalysisTask::getStartTime, now)
                .set(AnalysisTask::getUpdateTime, now));
        if (claimed == 0) {
            return;
        }

        AnalysisTask task = analysisTaskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        PullRequest pr = pullRequestMapper.selectById(task.getPrId());
        if (pr == null) {
            fail(taskId, TaskErrorCodeConstant.PR_INVALID, "PR 元数据不存在");
            return;
        }
        AnalysisConfigVO config = analysisConfigParser.parse(task.getAnalysisConfig(), taskId);

        try {
            updateProgress(taskId, TaskStatusConstant.STAGE_FETCH_PR, 10);
            updateProgress(taskId, TaskStatusConstant.STAGE_FETCH_FILES, 20);
            List<TaskFile> files = taskFileIngestionService.ingest(task, pr, config);
            log.info("任务 {} 文件拉取完成：files={}", taskId, files.size());

            updateProgress(taskId, TaskStatusConstant.STAGE_CONTEXT, 35);
            updateProgress(taskId, TaskStatusConstant.STAGE_AI, 50);
            AnalysisPipelineResult result = analysisPipeline.run(task, pr, config);

            updateProgress(taskId, TaskStatusConstant.STAGE_VALIDATE, 90);
            long skipped = countFiles(taskId, TaskFileConstant.STATUS_SKIPPED);
            long failed = countFiles(taskId, TaskFileConstant.STATUS_FAILED);

            int finalStatus;
            if (result.partial() || skipped > 0 || failed > 0) {
                int errorCode = result.errorCode() != null
                        ? result.errorCode()
                        : (failed > 0 ? TaskErrorCodeConstant.MODEL_FORMAT_ERROR
                        : TaskErrorCodeConstant.DIFF_TOO_LARGE);
                String message = result.errorMessage() != null
                        ? result.errorMessage()
                        : "部分文件未完成分析";
                finish(taskId, TaskStatusConstant.PARTIAL, errorCode, message);
                finalStatus = TaskStatusConstant.PARTIAL;
            } else {
                finish(taskId, TaskStatusConstant.COMPLETED, null, null);
                finalStatus = TaskStatusConstant.COMPLETED;
            }
            log.info("任务 {} 处理结束：status={}, durationMs={}", taskId,
                    finalStatus, System.currentTimeMillis() - started);
        } catch (TaskCanceledException ex) {
            log.info("任务 {} 已取消，worker 停止", taskId);
        } catch (AnalysisPipelineException ex) {
            fail(taskId, ex.getErrorCode(), ex.getMessage());
        } catch (ModelException ex) {
            log.warn("任务 {} 模型调用失败：{}", taskId, ex.getMessage());
            fail(taskId, ex.toTaskErrorCode(), "模型调用失败");
        } catch (GitHubApiException ex) {
            fail(taskId, GitHubErrorMapper.toTaskErrorCode(ex), "GitHub 请求失败");
        } catch (Exception ex) {
            log.error("任务 {} 分析失败", taskId, ex);
            fail(taskId, TaskErrorCodeConstant.INTERNAL_ERROR, "分析过程中发生内部错误");
        }
    }

    private void updateProgress(Long taskId, int stage, int progress) {
        LocalDateTime now = LocalDateTime.now();
        int updated = analysisTaskMapper.update(null, new LambdaUpdateWrapper<AnalysisTask>()
                .eq(AnalysisTask::getId, taskId)
                .eq(AnalysisTask::getStatus, TaskStatusConstant.RUNNING)
                .set(AnalysisTask::getStage, stage)
                .set(AnalysisTask::getProgress, progress)
                .set(AnalysisTask::getUpdateTime, now));
        if (updated == 0) {
            throw new TaskCanceledException();
        }
    }

    private void finish(Long taskId, int status, Integer errorCode, String errorMessage) {
        LocalDateTime now = LocalDateTime.now();
        analysisTaskMapper.update(null, new LambdaUpdateWrapper<AnalysisTask>()
                .eq(AnalysisTask::getId, taskId)
                .eq(AnalysisTask::getStatus, TaskStatusConstant.RUNNING)
                .set(AnalysisTask::getStatus, status)
                .set(AnalysisTask::getStage, TaskStatusConstant.STAGE_FINISHED)
                .set(AnalysisTask::getProgress, 100)
                .set(AnalysisTask::getErrorCode, errorCode)
                .set(AnalysisTask::getErrorMessage, errorMessage)
                .set(AnalysisTask::getFinishTime, now)
                .set(AnalysisTask::getUpdateTime, now));
    }

    private void fail(Long taskId, int errorCode, String errorMessage) {
        LocalDateTime now = LocalDateTime.now();
        analysisTaskMapper.update(null, new LambdaUpdateWrapper<AnalysisTask>()
                .eq(AnalysisTask::getId, taskId)
                .eq(AnalysisTask::getStatus, TaskStatusConstant.RUNNING)
                .set(AnalysisTask::getStatus, TaskStatusConstant.FAILED)
                .set(AnalysisTask::getErrorCode, errorCode)
                .set(AnalysisTask::getErrorMessage, errorMessage)
                .set(AnalysisTask::getFinishTime, now)
                .set(AnalysisTask::getUpdateTime, now));
    }

    private long countFiles(Long taskId, int status) {
        return taskFileMapper.selectCount(new LambdaQueryWrapper<TaskFile>()
                .eq(TaskFile::getTaskId, taskId)
                .eq(TaskFile::getStatus, status));
    }
}
