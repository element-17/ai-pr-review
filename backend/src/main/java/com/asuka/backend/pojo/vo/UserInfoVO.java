package com.asuka.backend.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 当前登录用户信息。
 */
@Data
@Builder
@Schema(description = "当前登录用户信息")
public class UserInfoVO {

    @Schema(description = "用户 ID")
    private Long id;

    @Schema(description = "登录名")
    private String username;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "头像地址")
    private String avatar;

    @Schema(description = "角色")
    private String role;
}
