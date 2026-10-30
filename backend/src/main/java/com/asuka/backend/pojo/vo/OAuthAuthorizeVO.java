package com.asuka.backend.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * GitHub OAuth 授权地址。
 */
@Data
@Builder
@Schema(description = "GitHub OAuth 授权地址")
public class OAuthAuthorizeVO {

    @Schema(description = "GitHub 授权页地址，前端直接跳转")
    private String authorizeUrl;

    @Schema(description = "服务端生成的一次性 state")
    private String state;
}
