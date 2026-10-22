package com.asuka.backend.config;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.asuka.backend.constant.AuthConstant;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sa-Token 角色与权限数据源。
 *
 * <p>登录时把用户角色写入 Sa-Token Session，这里直接读取，
 * 避免每次鉴权都查询数据库。角色变更后可让用户重新登录以刷新会话。
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        String role = getRole(loginId);
        if (AuthConstant.ROLE_ADMIN.equals(role)) {
            // 管理员拥有用户管理全部权限
            return List.of("user:read", "user:write", "user:delete");
        }
        // 普通用户只允许读取自己的信息
        return List.of("user:read");
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return List.of(getRole(loginId));
    }

    /**
     * 从登录会话中读取角色；没有设置时按普通用户处理，避免越权。
     */
    private String getRole(Object loginId) {
        SaSession session = StpUtil.getSessionByLoginId(loginId, false);
        Object role = session == null ? null : session.get("role");
        return role == null ? AuthConstant.ROLE_USER : role.toString();
    }
}
