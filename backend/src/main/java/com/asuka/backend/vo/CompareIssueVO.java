package com.asuka.backend.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 版本对比中的单条问题。
 */
@Data
@Schema(description = "版本对比问题")
public class CompareIssueVO {

    @Schema(description = "问题 ID：新增取目标、消失取基线、仍存在取目标")
    private Long issueId;

    @Schema(description = "仍存在问题的基线问题 ID")
    private Long baseIssueId;

    @Schema(description = "仍存在问题的目标问题 ID")
    private Long targetIssueId;

    private String title;

    private Integer category;

    private Integer severity;

    private Integer confidence;

    private String filePath;

    private Integer startLine;

    private Integer endLine;

    private Integer feedbackStatus;

    @Schema(description = "行号是否变化")
    private Boolean lineShifted;

    @Schema(description = "匹配置信度：low 表示仅按路径/类别/行号近似匹配")
    private String matchConfidence;
}
