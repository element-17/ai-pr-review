package com.asuka.backend.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 设置或修改登录密码。
 *
 * <p>GitHub 创建且尚未设置密码的账号可以只传 newPassword；
 * 已有密码的账号必须提供 currentPassword。
 */
@Data
@Schema(description = "设置或修改密码")
public class SetPasswordDTO {

    @Schema(description = "当前密码；已设置密码时必填")
    private String currentPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 32, message = "新密码长度需在 6-32 位之间")
    @Schema(description = "新密码")
    private String newPassword;

    @Schema(description = "确认新密码；传入时必须与 newPassword 一致")
    private String confirmPassword;
}
