package com.asuka.backend.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 结构化 diff 行。
 */
@Data
@Schema(description = "结构化 Diff 行")
public class DiffRowVO {

    @Schema(description = "行类型：hunk、add、delete、context、meta")
    private String type;

    @Schema(description = "旧文件侧行号；新增行和 hunk 头为空")
    private Integer oldLine;

    @Schema(description = "新文件侧行号；删除行和 hunk 头为空")
    private Integer newLine;

    @Schema(description = "行内容")
    private String content;

    @Schema(description = "覆盖该行的问题 ID")
    private List<Long> issueIds;

    @Schema(description = "覆盖该行的最高严重度：1-4，没有问题时为空")
    private Integer highestSeverity;
}
