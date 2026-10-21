package com.asuka.backend.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 文件上的问题摘要，用于 Diff 行标记。
 */
@Data
@Schema(description = "文件问题摘要")
public class IssueBriefVO {

    @Schema(description = "问题 ID")
    private Long issueId;

    @Schema(description = "问题标题")
    private String title;

    @Schema(description = "问题类别")
    private Integer category;

    @Schema(description = "严重度")
    private Integer severity;

    @Schema(description = "置信度 0-100")
    private Integer confidence;

    @Schema(description = "起始行号")
    private Integer startLine;

    @Schema(description = "结束行号")
    private Integer endLine;

    @Schema(description = "处理状态")
    private Integer feedbackStatus;
}
