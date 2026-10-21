package com.asuka.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 刷新 token 请求。
 *
 * <p>access-token 过期后，前端使用 refresh-token 换取新的 access-token 和 refresh-token。
 */
@Data
@Schema(description = "刷新 token 请求")
public class RefreshTokenDTO {

    @NotBlank(message = "refreshToken 不能为空")
    @Schema(description = "刷新令牌")
    private String refreshToken;
}
