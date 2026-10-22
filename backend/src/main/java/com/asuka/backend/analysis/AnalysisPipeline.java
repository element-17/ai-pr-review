package com.asuka.backend.analysis;

import com.asuka.backend.pojo.entity.AnalysisTask;
import com.asuka.backend.pojo.entity.PullRequest;
import com.asuka.backend.pojo.vo.AnalysisConfigVO;

/**
 * 分析 pipeline 抽象。
 *
 * <p>任务引擎只依赖该接口；F03 阶段使用 StubAnalysisPipeline 验证状态机，
 * F05/F06/F08/F10/F11/F12 阶段替换为真实 DeepSeek pipeline。
 */
public interface AnalysisPipeline {

    AnalysisResult run(AnalysisTask task, PullRequest pr, AnalysisConfigVO config);
}
