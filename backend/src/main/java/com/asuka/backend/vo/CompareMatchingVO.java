package com.asuka.backend.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 版本对比匹配规则说明。
 */
@Data
@Schema(description = "版本匹配说明")
public class CompareMatchingVO {

    private String method;

    private Boolean lineShiftWarning;

    private String note;
}
