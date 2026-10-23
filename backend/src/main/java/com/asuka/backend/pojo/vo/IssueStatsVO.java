package com.asuka.backend.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 问题处理进度与严重度分布。
 */
@Data
@Schema(description = "问题统计")
public class IssueStatsVO {

    @Schema(description = "问题总数")
    private Long total;

    @Schema(description = "待确认")
    private Long pending;

    @Schema(description = "已采纳")
    private Long accepted;

    @Schema(description = "已解决")
    private Long resolved;

    @Schema(description = "误报")
    private Long falsePositive;

    @Schema(description = "按严重度分布")
    private SeverityStatsVO bySeverity;
}
