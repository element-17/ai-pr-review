package com.asuka.backend.controller.user;

import com.asuka.backend.common.context.UserContext;
import com.asuka.backend.common.result.Result;
import com.asuka.backend.dto.UserSettingUpdateDTO;
import com.asuka.backend.service.UserSettingService;
import com.asuka.backend.vo.UserSettingVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户的设置接口。
 */
@Tag(name = "用户设置")
@RestController
@RequestMapping("/api/users/me/settings")
@RequiredArgsConstructor
@SecurityRequirement(name = "sa-token")
public class UserSettingController {

    private final UserSettingService userSettingService;

    @Operation(summary = "读取个人设置", description = "没有设置行时返回默认值")
    @GetMapping
    public Result<UserSettingVO> getSettings() {
        return Result.success(userSettingService.getSettings(UserContext.requireUserId()));
    }

    @Operation(summary = "修改个人设置", description = "支持部分更新，至少提交一个字段")
    @PatchMapping
    public Result<UserSettingVO> updateSettings(@Valid @RequestBody UserSettingUpdateDTO dto) {
        return Result.success(userSettingService.updateSettings(UserContext.requireUserId(), dto));
    }
}
