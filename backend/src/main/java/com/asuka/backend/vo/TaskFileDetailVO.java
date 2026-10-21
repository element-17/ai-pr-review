package com.asuka.backend.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 文件详情：在列表字段基础上返回 patch、GitHub 链接和关联问题。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "任务文件详情")
public class TaskFileDetailVO extends TaskFileVO {

    @Schema(description = "文件 diff 片段；二进制或未返回时为空")
    private String patch;

    @Schema(description = "GitHub 文件或 PR 页面链接")
    private String githubUrl;

    @Schema(description = "文件上的问题摘要")
    private List<IssueBriefVO> issues;
}
