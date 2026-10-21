package com.asuka.backend.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * PR 基本信息。
 */
@Data
@Schema(description = "PR 基本信息")
public class PrVO {

    @Schema(description = "仓库全名", example = "octocat/Hello-World")
    private String repoFullName;

    @Schema(description = "PR 编号", example = "1347")
    private Integer prNumber;

    @Schema(description = "PR 标题")
    private String title;

    @Schema(description = "PR 作者")
    private String author;

    @Schema(description = "PR 状态：1 open，2 closed，3 merged")
    private Integer state;

    @Schema(description = "目标分支")
    private String baseBranch;

    @Schema(description = "来源分支")
    private String headBranch;

    @Schema(description = "PR 当前最新 head SHA")
    private String headSha;

    @Schema(description = "新增行数")
    private Integer addLines;

    @Schema(description = "删除行数")
    private Integer deleteLines;

    @Schema(description = "变更文件数")
    private Integer fileCount;
}
