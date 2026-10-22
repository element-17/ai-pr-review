package com.asuka.backend.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 取消任务返回。
 */
@Data
@Schema(description = "任务操作结果")
public class TaskActionVO {

    @Schema(description = "任务 ID")
    private Long taskId;

    @Schema(description = "任务状态")
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "结束时间")
    private LocalDateTime finishTime;
}
