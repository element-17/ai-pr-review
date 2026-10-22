package com.asuka.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * PR 预览请求。
 */
@Data
@Schema(description = "PR 预览请求")
public class PrPreviewDTO {

    @NotBlank(message = "PR URL 不能为空")
    @Size(max = 500, message = "PR URL 不能超过 500 个字符")
    @Schema(description = "GitHub PR URL", example = "https://github.com/octocat/Hello-World/pull/11596")
    private String url;
}
