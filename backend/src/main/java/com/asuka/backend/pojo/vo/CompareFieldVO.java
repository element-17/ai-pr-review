package com.asuka.backend.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 版本对比中的单个总结字段。
 */
@Data
@Schema(description = "版本对比总结字段")
public class CompareFieldVO {

    @Schema(description = "基线值")
    private String base;

    @Schema(description = "目标值")
    private String target;

    @Schema(description = "是否发生变化")
    private Boolean changed;
}
