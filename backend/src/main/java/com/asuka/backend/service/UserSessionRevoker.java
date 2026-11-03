package com.asuka.backend.service;

import cn.dev33.satoken.stp.StpUtil;
import com.asuka.backend.common.constant.AuthConstant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.util.Set;

/** 禁用、删除或变更角色后撤销该账号已有的访问和刷新凭据。 */
@Component
@RequiredArgsConstructor
public class UserSessionRevoker {
    private final StringRedisTemplate redis;

    public void revoke(Long userId) {
        StpUtil.logout(userId);
        String userKey = AuthConstant.REFRESH_TOKEN_USER_KEY_PREFIX + userId;
        Set<String> tokens = redis.opsForSet().members(userKey);
        if (tokens != null && !tokens.isEmpty()) {
            redis.delete(tokens.stream().map(token -> AuthConstant.REFRESH_TOKEN_KEY_PREFIX + token).toList());
        }
        redis.delete(userKey);
    }
}
