package com.asuka.backend.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 按角色统计用户数量。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "角色用户统计")
public class RoleCountVO {

    @Schema(description = "角色", example = "ADMIN")
    private String role;

    @Schema(description = "用户数量", example = "1")
    private Long total;
}
