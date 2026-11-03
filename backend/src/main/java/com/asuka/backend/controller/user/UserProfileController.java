package com.asuka.backend.controller.user;

import com.asuka.backend.common.context.UserContext;
import com.asuka.backend.common.result.Result;
import com.asuka.backend.pojo.dto.UserProfileUpdateDTO;
import com.asuka.backend.pojo.dto.UserUpdateDTO;
import com.asuka.backend.pojo.vo.UserVO;
import com.asuka.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 当前用户的个人资料；用户标识只取自已验证的会话。 */
@RestController
@RequestMapping("/api/users/me/profile")
@RequiredArgsConstructor
public class UserProfileController {
    private final UserService userService;

    @GetMapping
    public Result<UserVO> get() {
        return Result.success(userService.getDetail(UserContext.requireUserId()));
    }

    @PatchMapping
    public Result<UserVO> update(@Valid @RequestBody UserProfileUpdateDTO profile) {
        Long userId = UserContext.requireUserId();
        UserUpdateDTO update = new UserUpdateDTO();
        update.setNickname(profile.getNickname());
        update.setEmail(profile.getEmail());
        update.setPhone(profile.getPhone());
        update.setAvatar(profile.getAvatar());
        userService.updateUser(userId, update);
        return Result.success(userService.getDetail(userId));
    }
}
