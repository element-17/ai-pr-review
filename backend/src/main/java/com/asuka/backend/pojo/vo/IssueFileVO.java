package com.asuka.backend.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 问题关联的文件。
 */
@Data
@Schema(description = "问题关联文件")
public class IssueFileVO {

    @Schema(description = "文件 ID")
    private Long fileId;

    @Schema(description = "文件路径")
    private String filePath;

    @Schema(description = "变更类型")
    private Integer changeType;
}
