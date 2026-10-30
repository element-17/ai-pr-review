package com.asuka.backend.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * GitHub 登录一次性 ticket 兑换请求。
 */
@Data
@Schema(description = "GitHub 登录 ticket 兑换")
public class OAuthTicketDTO {

    @NotBlank(message = "ticket 不能为空")
    @Schema(description = "GitHub 回调产生的一次性 ticket")
    private String ticket;
}
