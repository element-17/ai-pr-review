package com.asuka.backend.service;

import com.asuka.backend.pojo.vo.TaskActionVO;
import com.asuka.backend.pojo.vo.TaskDeleteVO;

/**
 * 任务取消与删除服务。
 */
public interface TaskLifecycleService {

    TaskActionVO cancel(Long taskId);

    TaskDeleteVO delete(Long taskId);
}
