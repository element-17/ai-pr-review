package com.asuka.backend.core.agent.ai;

import com.asuka.backend.pojo.vo.ModelTestResultVO;

/**
 * 模型配置测试连接。
 */
public interface ModelProbeService {

    ModelTestResultVO test(ModelRuntimeConfig config);
}
