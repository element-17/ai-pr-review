package com.asuka.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 分析产出的候选问题。
 *
 * <p>这是分析模块与持久化层之间的内部契约，不直接暴露给前端。
 * 入库前会校验文件归属、行号范围、类别、严重度和置信度。
 */
@Data
@Schema(description = "AI 分析产出的候选问题")
public class GeneratedIssueDTO {

    @Schema(description = "关联 task_file.id；报告级提示为空")
    private Long fileId;

    @NotBlank(message = "问题标题不能为空")
    @Size(max = 300, message = "问题标题不能超过 300 字")
    private String title;

    @Min(value = 1, message = "问题类别非法")
    @Max(value = 7, message = "问题类别非法")
    private Integer category;

    @Min(value = 1, message = "严重度非法")
    @Max(value = 4, message = "严重度非法")
    private Integer severity;

    @Min(value = 0, message = "置信度必须在 0-100 之间")
    @Max(value = 100, message = "置信度必须在 0-100 之间")
    private Integer confidence;

    @Min(value = 1, message = "起始行号必须大于 0")
    private Integer startLine;

    @Min(value = 1, message = "结束行号必须大于 0")
    private Integer endLine;

    @NotBlank(message = "触发条件不能为空")
    private String triggerCondition;

    @NotBlank(message = "潜在影响不能为空")
    private String impact;

    @Schema(description = "证据片段；为空表示证据不足")
    private String evidence;

    @NotBlank(message = "建议不能为空")
    private String suggestion;
}
