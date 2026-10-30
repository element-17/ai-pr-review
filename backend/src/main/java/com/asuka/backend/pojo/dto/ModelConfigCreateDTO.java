package com.asuka.backend.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * 新增模型配置请求。
 */
@Data
@Schema(description = "新增模型配置")
public class ModelConfigCreateDTO {

    @NotBlank(message = "显示名称不能为空")
    @Size(max = 100, message = "显示名称不能超过 100 个字符")
    private String displayName;

    @NotBlank(message = "provider 不能为空")
    @Pattern(regexp = "OPENAI_CHAT|ANTHROPIC_MESSAGES", message = "provider 只能是 OPENAI_CHAT 或 ANTHROPIC_MESSAGES")
    private String provider;

    @NotBlank(message = "base URL 不能为空")
    @Size(max = 500, message = "base URL 不能超过 500 个字符")
    private String baseUrl;

    @NotBlank(message = "model id 不能为空")
    @Size(max = 200, message = "model id 不能超过 200 个字符")
    private String modelId;

    @NotBlank(message = "API Key 不能为空")
    @Size(max = 1000, message = "API Key 过长")
    @ToString.Exclude
    private String apiKey;

    @Min(value = 1, message = "输出 token 上限不能小于 1")
    @Max(value = 200000, message = "输出 token 上限不能超过 200000")
    private Integer maxOutputTokens;

    @DecimalMin(value = "0.0", message = "temperature 不能小于 0")
    @DecimalMax(value = "2.0", message = "temperature 不能大于 2")
    private BigDecimal temperature;

    private Boolean enabled;

    private Boolean isDefault;
}
