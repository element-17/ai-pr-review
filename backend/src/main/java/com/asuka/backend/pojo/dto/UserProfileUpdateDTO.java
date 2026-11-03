package com.asuka.backend.pojo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 当前用户可修改的资料；不接受角色、状态或其他用户 ID。 */
@Data
public class UserProfileUpdateDTO {
    @Size(max = 50) private String nickname;
    @Email @Size(max = 100) private String email;
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$") private String phone;
    @Size(max = 255) @Pattern(regexp = "^$|^https?://[^\\s]+$", message = "头像地址必须是 HTTP(S) 地址") private String avatar;
}
