package com.asuka.backend.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * 跨域配置。
 *
 * <p>使用最高优先级的 CorsFilter 处理预检请求，避免 OPTIONS 请求进入
 * Sa-Token 登录拦截器后被当成未登录请求。允许已配置的前端来源及本地开发来源。
 */
@Configuration
public class WebMvcConfig {

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter(
            @Value("${pr.app.frontend-base-url:http://localhost}") String frontendBaseUrl) {
        CorsConfiguration configuration = new CorsConfiguration();
        URI frontend = URI.create(frontendBaseUrl);
        if (!List.of("http", "https").contains(frontend.getScheme())
                || frontend.getHost() == null || frontend.getUserInfo() != null) {
            throw new IllegalArgumentException("pr.app.frontend-base-url must be an HTTP(S) URL");
        }
        String origin = frontend.getScheme() + "://" + frontend.getRawAuthority();
        List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:*", "http://127.0.0.1:*"));
        allowedOrigins.add(origin);
        configuration.setAllowedOriginPatterns(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("X-Request-Id"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
