package com.asuka.backend.service;

import com.asuka.backend.pojo.vo.LoginVO;
import com.asuka.backend.pojo.vo.OAuthAuthorizeVO;

/**
 * GitHub OAuth 登录与绑定编排。
 */
public interface GitHubOAuthService {

    OAuthAuthorizeVO beginLogin();

    OAuthAuthorizeVO beginBind(Long userId);

    /**
     * 处理 GitHub 回调，返回前端重定向地址；不返回 token 明文。
     */
    String handleCallback(String code, String state, String error);

    /**
     * 用一次性 ticket 兑换平台双 token。
     */
    LoginVO exchangeTicket(String ticket);
}
