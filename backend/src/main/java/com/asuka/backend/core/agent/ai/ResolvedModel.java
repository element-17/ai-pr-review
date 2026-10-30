package com.asuka.backend.core.agent.ai;

import com.asuka.backend.common.constant.ModelSourceConstant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务实际使用的模型及来源。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolvedModel {

    /** 数据库配置 ID；环境兜底为空 */
    private Long modelConfigId;

    private String displayName;

    private String provider;

    private String modelId;

    private String source;

    private ModelRuntimeConfig runtimeConfig;

    public boolean isEnvFallback() {
        return ModelSourceConstant.ENV_FALLBACK.equals(source);
    }
}
