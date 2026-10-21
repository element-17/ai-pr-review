package com.asuka.backend.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 问题详情。
 */
@Data
@Schema(description = "问题详情")
public class IssueDetailVO {

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

    @Schema(description = "关联文件；报告级提示为空")
    private IssueFileVO file;

    @Schema(description = "起始行号")
    private Integer startLine;

    @Schema(description = "结束行号")
    private Integer endLine;

    @Schema(description = "触发条件")
    private String triggerCondition;

    @Schema(description = "潜在影响")
    private String impact;

    @Schema(description = "证据片段；为空表示证据不足")
    private String evidence;

    @Schema(description = "模型原始建议")
    private String aiSuggestion;

    @Schema(description = "当前建议")
    private String suggestion;

    @Schema(description = "处理状态")
    private Integer feedbackStatus;

    @Schema(description = "处理理由")
    private String feedbackRemark;

    @Schema(description = "最后反馈操作人；游客为空")
    private Long feedbackUserId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "最后反馈操作时间")
    private LocalDateTime feedbackTime;

    @Schema(description = "GitHub 链接；报告级提示为空")
    private String githubUrl;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
