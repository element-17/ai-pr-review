package com.asuka.backend.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 任务创建时固化的非敏感分析配置。
 */
@Data
@Schema(description = "分析配置快照")
public class AnalysisConfigVO {

    @Schema(description = "重点检查类别")
    private List<Integer> checkCategories;

    @Schema(description = "忽略目录")
    private List<String> ignoreDirs;

    @Schema(description = "最大文件数")
    private Integer maxFileCount;

    @Schema(description = "最大 Diff 行数")
    private Integer maxDiffLines;
}
