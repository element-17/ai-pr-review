package com.asuka.backend.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 问题列表项。
 */
@Data
@Schema(description = "评审问题")
public class IssueVO {

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

    @Schema(description = "文件 ID；报告级提示为空")
    private Long fileId;

    @Schema(description = "文件路径；报告级提示为空")
    private String filePath;

    @Schema(description = "起始行号")
    private Integer startLine;

    @Schema(description = "结束行号")
    private Integer endLine;

    @Schema(description = "处理状态")
    private Integer feedbackStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
