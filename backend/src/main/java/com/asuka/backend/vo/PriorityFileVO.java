package com.asuka.backend.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 重点阅读文件。
 */
@Data
@Schema(description = "重点阅读文件")
public class PriorityFileVO {

    @Schema(description = "文件 ID")
    private Long fileId;

    @Schema(description = "文件路径")
    private String filePath;

    @Schema(description = "重点原因")
    private String priorityReason;

    @Schema(description = "关联问题数")
    private Long issueCount;
}
