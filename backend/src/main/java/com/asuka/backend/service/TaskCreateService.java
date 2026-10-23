package com.asuka.backend.service;

import com.asuka.backend.pojo.dto.TaskCreateDTO;
import com.asuka.backend.pojo.vo.TaskCreateVO;

/**
 * 分析任务创建与重试服务。
 */
public interface TaskCreateService {

    TaskCreateVO create(TaskCreateDTO dto, String idempotencyKey);

    TaskCreateVO retry(Long taskId, String idempotencyKey);
}
