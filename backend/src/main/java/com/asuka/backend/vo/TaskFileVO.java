package com.asuka.backend.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 文件列表项。
 */
@Data
@Schema(description = "任务文件")
public class TaskFileVO {

    @Schema(description = "文件 ID")
    private Long fileId;

    @Schema(description = "文件路径")
    private String filePath;

    @Schema(description = "重命名前的路径")
    private String oldPath;

    @Schema(description = "变更类型：1 新增，2 修改，3 删除，4 重命名，5 复制")
    private Integer changeType;

    @Schema(description = "新增行数")
    private Integer addLines;

    @Schema(description = "删除行数")
    private Integer deleteLines;

    @Schema(description = "文件语言")
    private String language;

    @Schema(description = "是否二进制文件")
    private Boolean isBinary;

    @Schema(description = "分析状态：1 待分析，2 已分析，3 已跳过，4 分析失败")
    private Integer status;

    @Schema(description = "跳过或失败原因")
    private String skipReason;

    @Schema(description = "重点阅读原因")
    private String priorityReason;

    @Schema(description = "文件级变更简述")
    private String summary;
}
