package com.asuka.backend.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 系统用户。
 *
 * <p>password 标记为 select = false，普通 MP 查询不会带出密码；
 * 登录场景使用 SysUserMapper.selectByUsernameForLogin 显式查询。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
@Schema(description = "系统用户")
public class SysUser extends BaseEntity {

    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "用户 ID")
    private Long id;

    @Schema(description = "登录名")
    private String username;

    @TableField(select = false)
    @Schema(description = "BCrypt 加密后的密码", hidden = true)
    private String password;

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
}
