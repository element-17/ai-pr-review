package com.asuka.backend.service;

import com.asuka.backend.pojo.entity.AnalysisTask;

/**
 * 任务访问控制服务。
 */
public interface TaskAccessService {

    AnalysisTask requireTask(Long taskId);

    AnalysisTask requireAccessibleTask(Long taskId);

    Long currentUserIdOrNull();

    AnalysisTask requireOwnedTask(Long taskId);

    Long requireOptionalLogin();
}
