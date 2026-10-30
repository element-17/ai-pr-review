package com.asuka.backend.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 登录用户可选择的模型。
 */
@Data
@Schema(description = "可选模型")
public class ModelOptionVO {

    private Long id;

    private String displayName;

    private String provider;

    private Boolean isDefault;
}
