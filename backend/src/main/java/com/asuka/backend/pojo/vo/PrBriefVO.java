package com.asuka.backend.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 任务列表中的 PR 摘要。
 */
@Data
@Schema(description = "PR 摘要")
public class PrBriefVO {

    @Schema(description = "仓库全名", example = "octocat/Hello-World")
    private String repoFullName;

    @Schema(description = "PR 编号", example = "1347")
    private Integer prNumber;

    @Schema(description = "PR 标题")
    private String title;
}
