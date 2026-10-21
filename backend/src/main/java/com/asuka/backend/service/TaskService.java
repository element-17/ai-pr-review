package com.asuka.backend.service;

import com.asuka.backend.common.result.PageResult;
import com.asuka.backend.dto.TaskQueryDTO;
import com.asuka.backend.vo.TaskDetailVO;
import com.asuka.backend.vo.TaskListItemVO;
import com.asuka.backend.vo.TaskProgressVO;

/**
 * 分析任务查询服务。
 *
 * <p>本阶段只实现历史、详情和进度读取；任务创建、取消、重试属于 F03 异步任务模块。
 */
public interface TaskService {

    /**
     * 当前登录用户的历史任务分页。
     */
    PageResult<TaskListItemVO> pageMyTasks(TaskQueryDTO query);

    /**
     * 任务详情与工作台总览聚合。
     */
    TaskDetailVO getDetail(Long taskId);

    /**
     * 轻量进度轮询。
     */
    TaskProgressVO getProgress(Long taskId);
}
