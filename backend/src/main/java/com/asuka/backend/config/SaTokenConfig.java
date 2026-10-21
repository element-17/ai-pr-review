package com.asuka.backend.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import com.asuka.backend.common.interceptor.UserContextInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 登录校验配置。
 *
 * <p>所有 /api/** 接口默认要求登录；登录、刷新 token、注册和接口文档相关路径放行。
 * 具体接口的角色/权限校验通过 @SaCheckRole、@SaCheckPermission 注解完成。
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    private final UserContextInterceptor userContextInterceptor;

    public SaTokenConfig(UserContextInterceptor userContextInterceptor) {
        this.userContextInterceptor = userContextInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 先执行 Sa-Token 登录校验；未登录请求不会进入业务 Controller
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/refresh",
                        "/api/auth/register",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/doc.html",
                        "/webjars/**",
                        "/error"
                );

        // 登录校验通过后，把当前用户写入 ThreadLocal，供自动填充和业务代码使用
        registry.addInterceptor(userContextInterceptor)
                .addPathPatterns("/api/**");
    }
}
