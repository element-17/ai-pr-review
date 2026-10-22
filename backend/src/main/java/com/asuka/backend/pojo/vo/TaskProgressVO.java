package com.asuka.backend.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 轻量任务进度。
 */
@Data
@Schema(description = "任务进度")
public class TaskProgressVO {

    @Schema(description = "任务 ID")
    private Long taskId;

    @Schema(description = "任务状态")
    private Integer status;

    @Schema(description = "当前阶段")
    private Integer stage;

    @Schema(description = "进度 0-100")
    private Integer progress;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "结束时间")
    private LocalDateTime finishTime;

    @Schema(description = "失败或部分完成的原因编码")
    private Integer errorCode;

    @Schema(description = "失败或部分完成的原因摘要")
    private String errorMessage;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
