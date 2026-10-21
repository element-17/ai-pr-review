package com.asuka.backend.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 风险按严重度分布。
 */
@Data
@Schema(description = "风险严重度分布")
public class SeverityStatsVO {

    @Schema(description = "低")
    private Long low;

    @Schema(description = "中")
    private Long medium;

    @Schema(description = "高")
    private Long high;

    @Schema(description = "严重")
    private Long critical;
}
