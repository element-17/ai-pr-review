package com.asuka.backend.service;

import com.asuka.backend.common.constant.TaskStatusConstant;
import com.asuka.backend.common.exception.BusinessException;
import com.asuka.backend.entity.AnalysisTask;
import com.asuka.backend.mapper.AnalysisTaskMapper;
import com.asuka.backend.vo.TaskActionVO;
import com.asuka.backend.vo.TaskDeleteVO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 任务取消与删除。
 */
@Service
@RequiredArgsConstructor
public class TaskLifecycleService {

    private final AnalysisTaskMapper analysisTaskMapper;
    private final TaskAccessService taskAccessService;

    /**
     * 取消排队/分析中的任务；已取消幂等返回，已结束返回 409。
     */
    @Transactional(rollbackFor = Exception.class)
    public TaskActionVO cancel(Long taskId) {
        AnalysisTask task = taskAccessService.requireAccessibleTask(taskId);
        if (task.getStatus() != null && task.getStatus() == TaskStatusConstant.CANCELED) {
            return toActionVO(task);
        }
        if (!TaskStatusConstant.isRunning(task.getStatus())) {
            throw new BusinessException(409, "任务已结束，不能取消");
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = analysisTaskMapper.update(null, new LambdaUpdateWrapper<AnalysisTask>()
                .eq(AnalysisTask::getId, taskId)
                .in(AnalysisTask::getStatus, TaskStatusConstant.QUEUED, TaskStatusConstant.RUNNING)
                .set(AnalysisTask::getStatus, TaskStatusConstant.CANCELED)
                .set(AnalysisTask::getFinishTime, now)
                .set(AnalysisTask::getUpdateTime, now));
        if (updated == 0) {
            AnalysisTask latest = analysisTaskMapper.selectById(taskId);
            if (latest != null && latest.getStatus() != null
                    && latest.getStatus() == TaskStatusConstant.CANCELED) {
                return toActionVO(latest);
            }
            throw new BusinessException(409, "任务状态已变化，无法取消");
        }

        AnalysisTask latest = analysisTaskMapper.selectById(taskId);
        return toActionVO(latest);
    }

    /**
     * 逻辑删除本人已完成/失败/取消的任务。
     */
    @Transactional(rollbackFor = Exception.class)
    public TaskDeleteVO delete(Long taskId) {
        AnalysisTask task = taskAccessService.requireOwnedTask(taskId);
        if (TaskStatusConstant.isRunning(task.getStatus())) {
            throw new BusinessException(409, "任务仍在执行，请先取消");
        }
        analysisTaskMapper.deleteById(taskId);

        TaskDeleteVO vo = new TaskDeleteVO();
        vo.setTaskId(taskId);
        vo.setDeleted(true);
        return vo;
    }

    private TaskActionVO toActionVO(AnalysisTask task) {
        TaskActionVO vo = new TaskActionVO();
        vo.setTaskId(task.getId());
        vo.setStatus(task.getStatus());
        vo.setFinishTime(task.getFinishTime());
        return vo;
    }
}
