package com.asuka.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建分析任务请求。
 */
@Data
@Schema(description = "创建分析任务请求")
public class TaskCreateDTO {

    @NotBlank(message = "PR URL 不能为空")
    @Size(max = 500, message = "PR URL 不能超过 500 个字符")
    @Schema(description = "GitHub PR URL")
    private String url;

    @Pattern(regexp = "zh-CN|en-US", message = "报告语言只能是 zh-CN 或 en-US")
    @Schema(description = "报告语言；不填时登录用户取设置，游客默认 zh-CN")
    private String reportLanguage;
}
