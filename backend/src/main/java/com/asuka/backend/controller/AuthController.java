package com.asuka.backend.controller;

import com.asuka.backend.common.result.Result;
import com.asuka.backend.dto.LoginDTO;
import com.asuka.backend.dto.RefreshTokenDTO;
import com.asuka.backend.dto.RegisterDTO;
import com.asuka.backend.service.AuthService;
import com.asuka.backend.vo.LoginVO;
import com.asuka.backend.vo.UserInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 *
 * <p>login、refresh、register 在 SaTokenConfig 中放行；
 * logout、me 需要携带 access-token。
 */
@Tag(name = "认证管理")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "登录", description = "校验用户名密码，返回 access-token 和 refresh-token")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success(authService.login(dto));
    }

    @Operation(summary = "刷新 token", description = "使用 refresh-token 换取新的双 token，旧 refresh-token 立即失效")
    @PostMapping("/refresh")
    public Result<LoginVO> refresh(@Valid @RequestBody RefreshTokenDTO dto) {
        return Result.success(authService.refresh(dto.getRefreshToken()));
    }

    @Operation(summary = "注册", description = "注册普通用户，角色固定为 USER")
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterDTO dto) {
        authService.register(dto);
        return Result.success();
    }

    @Operation(summary = "退出登录", description = "注销当前 access-token 并撤销该用户所有 refresh-token")
    @SecurityRequirement(name = "sa-token")
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.success();
    }

    @Operation(summary = "当前用户", description = "获取当前登录用户的基本信息")
    @SecurityRequirement(name = "sa-token")
    @GetMapping("/me")
    public Result<UserInfoVO> me() {
        return Result.success(authService.currentUser());
    }
}
