package com.asuka.backend.common.constant;

/**
 * 认证相关常量。
 *
 * <p>Redis key 统一使用业务前缀，便于在 redis-cli 中排查和批量清理。
 */
public final class AuthConstant {

    /** refresh-token -> userId */
    public static final String REFRESH_TOKEN_KEY_PREFIX = "auth:refresh:";

    /** userId -> refresh-token 集合，用于退出登录时批量撤销 */
    public static final String REFRESH_TOKEN_USER_KEY_PREFIX = "auth:refresh:user:";

    /** 登录失败次数 -> username */
    public static final String LOGIN_FAIL_KEY_PREFIX = "auth:login:fail:";

    /** refresh-token 有效期：7 天 */
    public static final long REFRESH_TOKEN_TTL_DAYS = 7;

    /** 登录失败锁定时长：15 分钟 */
    public static final long LOGIN_FAIL_TTL_MINUTES = 15;

    /** 连续登录失败上限 */
    public static final int LOGIN_FAIL_LIMIT = 5;

    /** 返回给前端的 token 类型 */
    public static final String TOKEN_TYPE = "Bearer";

    /** 管理员角色 */
    public static final String ROLE_ADMIN = "ADMIN";

    /** 普通用户角色 */
    public static final String ROLE_USER = "USER";

    private AuthConstant() {
    }
}
