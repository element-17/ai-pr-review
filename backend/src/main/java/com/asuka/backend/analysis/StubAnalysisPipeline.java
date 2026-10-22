package com.asuka.backend.analysis;

import com.asuka.backend.common.constant.TaskFileConstant;
import com.asuka.backend.common.exception.TaskCanceledException;
import com.asuka.backend.common.properties.TaskProperties;
import com.asuka.backend.pojo.entity.AnalysisReport;
import com.asuka.backend.pojo.entity.AnalysisTask;
import com.asuka.backend.pojo.entity.PullRequest;
import com.asuka.backend.pojo.entity.TaskFile;
import com.asuka.backend.mapper.AnalysisReportMapper;
import com.asuka.backend.mapper.TaskFileMapper;
import com.asuka.backend.pojo.vo.AnalysisConfigVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 状态机验证用的 stub pipeline。
 *
 * <p>不调用模型，只把 pending 文件标记为已分析并写一条报告；
 * 用于 F03/F04/F17 分支独立验证异步执行、取消、重试和恢复。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pr.ai.stub-pipeline", havingValue = "true", matchIfMissing = false)
public class StubAnalysisPipeline implements AnalysisPipeline {

    private final TaskFileMapper taskFileMapper;
    private final AnalysisReportMapper analysisReportMapper;
    private final TaskProperties taskProperties;

    @Override
    public AnalysisResult run(AnalysisTask task, PullRequest pr, AnalysisConfigVO config) {
        if (taskProperties.getStubDelayMs() > 0) {
            try {
                Thread.sleep(taskProperties.getStubDelayMs());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new TaskCanceledException();
            }
        }

        LocalDateTime now = LocalDateTime.now();
        taskFileMapper.update(null, new LambdaUpdateWrapper<TaskFile>()
                .eq(TaskFile::getTaskId, task.getId())
                .eq(TaskFile::getStatus, TaskFileConstant.STATUS_PENDING)
                .set(TaskFile::getStatus, TaskFileConstant.STATUS_ANALYZED)
                .set(TaskFile::getSummary, "Stub pipeline 已读取文件变更。")
                .set(TaskFile::getUpdateTime, now));

        AnalysisReport report = analysisReportMapper.selectOne(new LambdaQueryWrapper<AnalysisReport>()
                .eq(AnalysisReport::getTaskId, task.getId())
                .last("LIMIT 1"));
        if (report == null) {
            report = new AnalysisReport();
            report.setTaskId(task.getId());
            report.setPurpose("Stub pipeline 已完成 PR 变更读取。");
            report.setChanges("用于验证异步任务状态机和报告读取链路。");
            report.setImpacts("无。");
            analysisReportMapper.insert(report);
        }

        log.info("Stub pipeline 完成任务 {}", task.getId());
        return AnalysisResult.completed();
    }
}
