package com.asuka.backend.common.context;

import com.asuka.backend.common.exception.BusinessException;

/**
 * 当前登录用户上下文。
 *
 * <p>请求进入时由拦截器写入，请求结束时清理，避免 ThreadLocal 在线程池中串数据。
 * MyBatis-Plus 的自动填充处理器会从这里读取 createUser / updateUser。
 */
public final class UserContext {

    private static final ThreadLocal<Long> CURRENT_USER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void setUserId(Long userId) {
        CURRENT_USER.set(userId);
    }

    public static Long getUserId() {
        return CURRENT_USER.get();
    }

    public static Long requireUserId() {
        Long userId = CURRENT_USER.get();
        if (userId == null) {
            throw new BusinessException(401, "登录状态已失效，请重新登录");
        }
        return userId;
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
