package com.asuka.backend.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 可复用的历史任务提示。
 */
@Data
@Schema(description = "可复用任务")
public class ReusableTaskVO {

    @Schema(description = "任务 ID")
    private Long taskId;

    @Schema(description = "任务状态")
    private Integer status;

    @Schema(description = "报告语言")
    private String reportLanguage;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "结束时间")
    private LocalDateTime finishTime;
}
