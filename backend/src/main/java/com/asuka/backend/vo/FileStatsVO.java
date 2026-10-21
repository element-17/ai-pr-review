package com.asuka.backend.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 任务文件覆盖统计。
 */
@Data
@Schema(description = "文件覆盖统计")
public class FileStatsVO {

    @Schema(description = "文件总数")
    private Long total;

    @Schema(description = "已分析文件数")
    private Long analyzed;

    @Schema(description = "已跳过文件数")
    private Long skipped;

    @Schema(description = "分析失败文件数")
    private Long failed;
}
