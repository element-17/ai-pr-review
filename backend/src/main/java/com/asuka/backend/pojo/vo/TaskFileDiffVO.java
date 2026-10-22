package com.asuka.backend.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 文件结构化 Diff：在文件详情基础上返回可渲染行和问题标记。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "文件结构化 Diff")
public class TaskFileDiffVO extends TaskFileDetailVO {

    @Schema(description = "是否返回了可解析的 patch")
    private Boolean patchAvailable;

    @Schema(description = "结构化行；二进制/无 patch 时为空")
    private List<DiffRowVO> rows;

    @Schema(description = "新文件侧新增行号")
    private List<Integer> addedLines;

    @Schema(description = "hunk 覆盖的新文件侧行号")
    private List<Integer> hunkLines;
}
