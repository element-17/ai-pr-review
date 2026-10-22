package com.asuka.backend.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 删除任务返回。
 */
@Data
@Schema(description = "删除任务结果")
public class TaskDeleteVO {

    @Schema(description = "任务 ID")
    private Long taskId;

    @Schema(description = "是否已删除")
    private Boolean deleted;
}
