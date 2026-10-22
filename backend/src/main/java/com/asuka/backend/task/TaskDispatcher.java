package com.asuka.backend.task;

import com.asuka.backend.common.constant.TaskStatusConstant;
import com.asuka.backend.pojo.entity.AnalysisTask;
import com.asuka.backend.mapper.AnalysisTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 排队任务调度器。
 *
 * <p>每 5 秒扫描 status=1 的任务，投递到固定线程池；
 * 数据库 claim 保证多个调度/重复投递不会重复执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskDispatcher {

    private final AnalysisTaskMapper analysisTaskMapper;
    private final TaskWorker taskWorker;

    @Qualifier("analysisTaskExecutor")
    private final ThreadPoolTaskExecutor analysisTaskExecutor;

    @Scheduled(fixedDelayString = "${pr.task.dispatch-interval-ms:5000}")
    public void dispatch() {
        List<AnalysisTask> queued = analysisTaskMapper.selectList(new LambdaQueryWrapper<AnalysisTask>()
                .eq(AnalysisTask::getStatus, TaskStatusConstant.QUEUED)
                .orderByAsc(AnalysisTask::getId)
                .last("LIMIT 20"));
        for (AnalysisTask task : queued) {
            try {
                analysisTaskExecutor.execute(() -> taskWorker.process(task.getId()));
            } catch (TaskRejectedException ex) {
                log.warn("分析线程池队列已满，任务 {} 等待下一轮调度", task.getId());
                break;
            }
        }
    }
}
