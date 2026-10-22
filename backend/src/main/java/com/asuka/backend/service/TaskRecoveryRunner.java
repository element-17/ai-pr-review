package com.asuka.backend.service;

import com.asuka.backend.common.constant.TaskErrorCodeConstant;
import com.asuka.backend.common.constant.TaskStatusConstant;
import com.asuka.backend.entity.AnalysisTask;
import com.asuka.backend.mapper.AnalysisTaskMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 启动恢复。
 *
 * <p>服务重启时，运行中任务无法确认执行到哪一步，统一标记失败 errorCode=8；
 * 排队任务保留，由 dispatcher 重新扫描投递。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskRecoveryRunner implements ApplicationRunner {

    private final AnalysisTaskMapper analysisTaskMapper;
    private final TaskDispatcher taskDispatcher;

    @Override
    public void run(ApplicationArguments args) {
        LocalDateTime now = LocalDateTime.now();
        int recovered = analysisTaskMapper.update(null, new LambdaUpdateWrapper<AnalysisTask>()
                .eq(AnalysisTask::getStatus, TaskStatusConstant.RUNNING)
                .set(AnalysisTask::getStatus, TaskStatusConstant.FAILED)
                .set(AnalysisTask::getErrorCode, TaskErrorCodeConstant.INTERNAL_ERROR)
                .set(AnalysisTask::getErrorMessage, "服务重启导致任务中断")
                .set(AnalysisTask::getFinishTime, now)
                .set(AnalysisTask::getUpdateTime, now));
        if (recovered > 0) {
            log.warn("启动恢复：{} 条运行中任务已标记失败", recovered);
        }

        // 立即扫描一次排队任务，避免等下一个调度周期
        taskDispatcher.dispatch();
    }
}
