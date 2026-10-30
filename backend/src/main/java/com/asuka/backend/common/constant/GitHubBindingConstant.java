package com.asuka.backend.common.constant;

/**
 * GitHub 绑定令牌状态。
 */
public final class GitHubBindingConstant {

    /** 令牌有效 */
    public static final int TOKEN_ACTIVE = 1;

    /** 令牌失效，需要用户重新授权 */
    public static final int TOKEN_INVALID = 2;

    /** 用户主动解绑或令牌已撤销 */
    public static final int TOKEN_REVOKED = 3;

    private GitHubBindingConstant() {
    }
}
