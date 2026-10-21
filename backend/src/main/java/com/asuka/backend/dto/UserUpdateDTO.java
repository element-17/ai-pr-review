package com.asuka.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员更新用户请求。
 *
 * <p>用户名和密码不允许通过该接口修改，密码应走独立的修改密码接口。
 */
@Data
@Schema(description = "更新用户请求")
public class UserUpdateDTO {

    @Size(max = 50, message = "昵称长度不能超过 50 个字符")
    @Schema(description = "昵称")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过 100 个字符")
    @Schema(description = "邮箱")
    private String email;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "手机号")
    private String phone;

    @Size(max = 255, message = "头像地址长度不能超过 255 个字符")
    @Schema(description = "头像地址")
    private String avatar;

    @Min(value = 0, message = "状态只能是 0 或 1")
    @Max(value = 1, message = "状态只能是 0 或 1")
    @Schema(description = "状态：1 启用，0 禁用")
    private Integer status;

    @Pattern(regexp = "^$|ADMIN|USER", message = "角色只能是 ADMIN 或 USER")
    @Schema(description = "角色：ADMIN / USER")
    private String role;
}
