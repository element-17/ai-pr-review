package com.asuka.backend.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 登录成功返回。
 *
 * <p>access-token 用于访问业务接口；refresh-token 只用于换取新 token。
 * 前端建议把 refresh-token 存在更安全的位置（如 HttpOnly Cookie），
 * 当前骨架为方便演示，直接放在响应体中。
 */
@Data
@Builder
@Schema(description = "登录结果")
public class LoginVO {

    @Schema(description = "访问令牌，放在 Authorization: Bearer <token> 中")
    private String accessToken;

    @Schema(description = "刷新令牌，access-token 过期后使用")
    private String refreshToken;

    @Schema(description = "令牌类型", example = "Bearer")
    private String tokenType;

    @Schema(description = "access-token 有效期（秒）", example = "1800")
    private long expiresIn;

    @Schema(description = "当前用户信息")
    private UserInfoVO userInfo;
}
