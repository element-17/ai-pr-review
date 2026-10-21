package com.asuka.backend.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户列表/详情返回。
 *
 * <p>不包含 password、deleted 等敏感或内部字段。
 */
@Data
@Schema(description = "用户信息")
public class UserVO {

    @Schema(description = "用户 ID")
    private Long id;

    @Schema(description = "登录名")
    private String username;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "头像地址")
    private String avatar;

    @Schema(description = "状态：1 启用，0 禁用")
    private Integer status;

    @Schema(description = "角色：ADMIN / USER")
    private String role;

    @Schema(description = "最后登录时间")
    private LocalDateTime lastLoginTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
