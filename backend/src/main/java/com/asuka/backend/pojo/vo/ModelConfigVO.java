package com.asuka.backend.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理员模型配置视图。永远不返回 API Key 明文或密文。
 */
@Data
@Schema(description = "模型配置")
public class ModelConfigVO {

    private Long id;

    private String displayName;

    private String provider;

    private String baseUrl;

    private String modelId;

    @Schema(description = "是否已配置 API Key")
    private Boolean apiKeyConfigured;

    @Schema(description = "API Key 掩码，例如 ****abcd")
    private String apiKeyHint;

    private Integer maxOutputTokens;

    private BigDecimal temperature;

    private Boolean enabled;

    private Boolean isDefault;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
