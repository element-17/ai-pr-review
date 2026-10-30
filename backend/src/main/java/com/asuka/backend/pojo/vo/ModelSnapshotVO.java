package com.asuka.backend.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 任务模型快照。只包含可展示、非敏感字段，不记录 base URL 或 API Key。
 */
@Data
@Schema(description = "任务模型快照")
public class ModelSnapshotVO {

    private Long configId;

    private String displayName;

    private String provider;

    private String modelId;

    /** USER_PREFERENCE / ADMIN_DEFAULT / ENV_FALLBACK / ORIGINAL_TASK */
    private String source;
}
