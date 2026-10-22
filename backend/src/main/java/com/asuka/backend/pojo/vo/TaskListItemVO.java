package com.asuka.backend.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 历史任务列表项。
 */
@Data
@Schema(description = "历史任务列表项")
public class TaskListItemVO {

    @Schema(description = "任务 ID")
    private Long taskId;

    @Schema(description = "PR 摘要")
    private PrBriefVO pr;

    @Schema(description = "任务固定的 head SHA")
    private String headSha;

    @Schema(description = "任务状态")
    private Integer status;

    @Schema(description = "当前阶段")
    private Integer stage;

    @Schema(description = "进度 0-100")
    private Integer progress;

    @Schema(description = "报告语言")
    private String reportLanguage;

    @Schema(description = "PR 有新提交后旧任务为 true")
    private Boolean isExpired;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "结束时间")
    private LocalDateTime finishTime;
}
