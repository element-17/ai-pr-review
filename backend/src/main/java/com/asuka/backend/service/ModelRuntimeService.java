package com.asuka.backend.service;

import com.asuka.backend.core.agent.ai.ResolvedModel;
import com.asuka.backend.pojo.entity.AnalysisTask;
import com.asuka.backend.pojo.vo.ModelSnapshotVO;

/**
 * 任务创建/执行时的模型解析。
 *
 * <p>优先级由上层策略决定：M5 接入用户偏好后为
 * 用户偏好 → 管理员默认 → 环境兜底；重试优先沿用原任务模型。
 */
public interface ModelRuntimeService {

    ResolvedModel resolveForUser(Long userId);

    ResolvedModel resolveForGuest();

    ResolvedModel resolveForExecution(AnalysisTask task);

    ResolvedModel resolveForRetry(AnalysisTask original);

    ModelSnapshotVO toSnapshot(ResolvedModel model);
}
