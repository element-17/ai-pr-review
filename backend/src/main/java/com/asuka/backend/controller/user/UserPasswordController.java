package com.asuka.backend.controller.user;

import com.asuka.backend.common.context.UserContext;
import com.asuka.backend.common.result.Result;
import com.asuka.backend.pojo.dto.SetPasswordDTO;
import com.asuka.backend.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户密码设置/修改。
 */
@Tag(name = "用户账号")
@RestController
@RequestMapping("/api/users/me/password")
@RequiredArgsConstructor
@SecurityRequirement(name = "sa-token")
public class UserPasswordController {

    private final AuthService authService;

    @Operation(summary = "设置或修改密码", description = "GitHub 创建且未设置密码的账号首次设置不需要旧密码")
    @PutMapping
    public Result<Void> setPassword(@Valid @RequestBody SetPasswordDTO dto) {
        authService.setPassword(UserContext.requireUserId(), dto);
        return Result.success();
    }
}
