package com.asuka.backend.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.asuka.backend.common.constant.AuthConstant;
import com.asuka.backend.common.result.PageResult;
import com.asuka.backend.common.result.Result;
import com.asuka.backend.dto.UserCreateDTO;
import com.asuka.backend.dto.UserQueryDTO;
import com.asuka.backend.dto.UserUpdateDTO;
import com.asuka.backend.service.SysUserService;
import com.asuka.backend.vo.RoleCountVO;
import com.asuka.backend.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户管理接口。
 *
 * <p>整个 Controller 要求 ADMIN 角色；普通用户只能访问 /api/auth/me。
 * 单表 CRUD 使用 MyBatis-Plus；复杂条件查询和统计走 Mapper XML。
 */
@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Validated
@SaCheckRole(AuthConstant.ROLE_ADMIN)
@SecurityRequirement(name = "sa-token")
public class SysUserController {

    private final SysUserService sysUserService;

    @Operation(summary = "分页查询用户（MyBatis-Plus）", description = "常规单表条件分页")
    @GetMapping("/page")
    public Result<PageResult<UserVO>> page(@Valid UserQueryDTO query) {
        return Result.success(sysUserService.page(query));
    }

    @Operation(summary = "复杂条件查询用户（原生 XML）", description = "关键字同时匹配用户名、昵称、邮箱")
    @GetMapping("/search")
    public Result<PageResult<UserVO>> search(@Valid UserQueryDTO query) {
        return Result.success(sysUserService.nativePage(query));
    }

    @Operation(summary = "用户详情")
    @GetMapping("/{id}")
    public Result<UserVO> detail(@Parameter(description = "用户 ID") @PathVariable Long id) {
        return Result.success(sysUserService.getDetail(id));
    }

    @Operation(summary = "新增用户")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody UserCreateDTO dto) {
        return Result.success(sysUserService.createUser(dto));
    }

    @Operation(summary = "更新用户")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UserUpdateDTO dto) {
        sysUserService.updateUser(id, dto);
        return Result.success();
    }

    @Operation(summary = "删除用户", description = "逻辑删除，不会物理删除数据")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysUserService.deleteUser(id);
        return Result.success();
    }

    @Operation(summary = "启用/禁用用户")
    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(
            @PathVariable Long id,
            @RequestParam @Min(value = 0, message = "状态只能是 0 或 1")
            @Max(value = 1, message = "状态只能是 0 或 1") Integer status) {
        sysUserService.changeStatus(id, status);
        return Result.success();
    }

    @Operation(summary = "按角色统计用户数量", description = "原生 SQL GROUP BY 示例")
    @GetMapping("/stats/roles")
    public Result<List<RoleCountVO>> roleStats() {
        return Result.success(sysUserService.roleStats());
    }
}
