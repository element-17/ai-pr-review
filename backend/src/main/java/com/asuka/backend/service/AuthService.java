package com.asuka.backend.service;

import com.asuka.backend.dto.LoginDTO;
import com.asuka.backend.dto.RegisterDTO;
import com.asuka.backend.vo.LoginVO;
import com.asuka.backend.vo.UserInfoVO;

/**
 * 认证服务。
 */
public interface AuthService {

    /**
     * 登录并签发 access-token + refresh-token。
     */
    LoginVO login(LoginDTO dto);

    /**
     * 使用 refresh-token 换取新的双 token。
     */
    LoginVO refresh(String refreshToken);

    /**
     * 退出登录：注销 access-token 并撤销该用户所有 refresh-token。
     */
    void logout();

    /**
     * 获取当前登录用户信息。
     */
    UserInfoVO currentUser();

    /**
     * 注册普通用户。
     */
    void register(RegisterDTO dto);
}
