package com.asuka.backend.interceptor;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import com.asuka.backend.context.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录用户上下文拦截器。
 *
 * <p>Sa-Token 的登录校验拦截器先执行，校验通过后再把 userId 写入 ThreadLocal，
 * 供 MyBatis-Plus 自动填充和业务代码使用。
 */
@Component
public class UserContextInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        try {
            if (StpUtil.isLogin()) {
                UserContext.setUserId(StpUtil.getLoginIdAsLong());
            }
        } catch (NotLoginException ignored) {
            // 任务子资源允许游客令牌访问；无效或过期的 Authorization 按未登录处理，
            // 真正的游客令牌校验在 TaskAccessService 中完成。
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 线程池复用线程，必须在请求结束时清理，避免上一个请求的用户信息串到下一个请求
        UserContext.clear();
    }
}
