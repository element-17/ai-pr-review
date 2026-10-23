package com.asuka.backend.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分析报告总结与覆盖范围。
 */
@Data
@Schema(description = "分析报告")
public class ReportVO {

    @Schema(description = "任务 ID")
    private Long taskId;

    @Schema(description = "报告语言")
    private String reportLanguage;

    @Schema(description = "使用的模型")
    private String modelName;

    @Schema(description = "本次分析的非敏感配置")
    private AnalysisConfigVO analysisConfig;

    @Schema(description = "PR 修改目的")
    private String purpose;

    @Schema(description = "主要变更")
    private String changes;

    @Schema(description = "可能影响的模块")
    private String impacts;

    @Schema(description = "覆盖统计")
    private CoverageVO coverage;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "报告生成时间")
    private LocalDateTime generatedAt;
}
