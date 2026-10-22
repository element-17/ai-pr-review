package com.asuka.backend.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 用户分页查询条件。
 */
@Data
@Schema(description = "用户分页查询条件")
public class UserQueryDTO {

    @Min(value = 1, message = "页码不能小于 1")
    @Schema(description = "页码，从 1 开始", example = "1")
    private long page = 1;

    @Min(value = 1, message = "每页数量不能小于 1")
    @Max(value = 100, message = "每页数量不能超过 100")
    @Schema(description = "每页数量", example = "10")
    private long pageSize = 10;

    @Schema(description = "关键字：用户名、昵称或邮箱")
    private String keyword;

    @Schema(description = "状态：1 启用，0 禁用")
    private Integer status;

    @Schema(description = "角色：ADMIN / USER")
    private String role;
}
