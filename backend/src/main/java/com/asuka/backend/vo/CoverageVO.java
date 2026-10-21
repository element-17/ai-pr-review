package com.asuka.backend.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 分析覆盖统计。
 */
@Data
@Schema(description = "分析覆盖统计")
public class CoverageVO {

    @Schema(description = "文件总数")
    private Long totalFiles;

    @Schema(description = "已分析文件数")
    private Long analyzedFiles;

    @Schema(description = "已跳过文件数")
    private Long skippedFiles;

    @Schema(description = "分析失败文件数")
    private Long failedFiles;
}
