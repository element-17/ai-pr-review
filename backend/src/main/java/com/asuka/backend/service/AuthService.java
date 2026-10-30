package com.asuka.backend.service;

import com.asuka.backend.pojo.dto.LoginDTO;
import com.asuka.backend.pojo.dto.RegisterDTO;
import com.asuka.backend.pojo.dto.SetPasswordDTO;
import com.asuka.backend.pojo.vo.LoginVO;
import com.asuka.backend.pojo.vo.UserInfoVO;

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

    /**
     * 为已校验的本地用户签发双 token；供 GitHub 登录 ticket 兑换复用。
     */
    LoginVO issueTokensForUser(Long userId);

    /**
     * 设置或修改密码。GitHub 创建且未设置密码的账号可以不带旧密码。
     */
    void setPassword(Long userId, SetPasswordDTO dto);
}
