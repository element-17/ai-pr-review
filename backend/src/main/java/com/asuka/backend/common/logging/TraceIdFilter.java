package com.asuka.backend.common.logging;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 请求 traceId 与访问日志。
 *
 * <p>只记录方法、路径、状态、耗时、来源 IP 和登录用户 ID；
 * 不记录 query、请求体、请求头和响应体，避免游客令牌、密码等进入日志。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID = "traceId";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private static final Logger ACCESS_LOG = LoggerFactory.getLogger("com.asuka.backend.access");
    private static final Pattern REQUEST_ID_PATTERN = Pattern.compile("[A-Za-z0-9._-]{8,64}");
    private static final String[] EXCLUDED_PREFIXES = {
            "/favicon.ico", "/hybridaction/", "/error", "/v3/api-docs",
            "/swagger-ui", "/webjars/", "/actuator/"
    };

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String prefix : EXCLUDED_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolveTraceId(request);
        MDC.put(TRACE_ID, traceId);
        response.setHeader(REQUEST_ID_HEADER, traceId);

        long started = System.currentTimeMillis();
        Exception failure = null;
        try {
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            failure = ex;
            throw ex;
        } finally {
            long durationMs = System.currentTimeMillis() - started;
            int status = failure == null ? response.getStatus() : 500;
            String userId = currentUserId(request);
            String message = "access method={} path={} status={} durationMs={} ip={} userId={}";
            if (status >= 500) {
                ACCESS_LOG.warn(message, request.getMethod(), request.getRequestURI(),
                        status, durationMs, clientIp(request), userId);
            } else {
                ACCESS_LOG.info(message, request.getMethod(), request.getRequestURI(),
                        status, durationMs, clientIp(request), userId);
            }
            MDC.remove(TRACE_ID);
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        String provided = request.getHeader(REQUEST_ID_HEADER);
        if (StringUtils.hasText(provided) && REQUEST_ID_PATTERN.matcher(provided).matches()) {
            return provided;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String currentUserId(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            return "-";
        }
        String token = authorization.substring("Bearer ".length()).trim();
        if (!StringUtils.hasText(token)) {
            return "-";
        }
        try {
            // 直接按 token 查询，避免 Sa-Token 在 MVC 完成后清理上下文导致 userId 丢失
            Object loginId = StpUtil.getLoginIdByToken(token);
            return loginId == null ? "-" : String.valueOf(loginId);
        } catch (Exception ignored) {
            // token 无效或 Redis 不可用时不影响访问日志
            return "-";
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            String first = forwarded.split(",")[0].trim();
            return first.length() > 64 ? first.substring(0, 64) : first;
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.length() > 64 ? realIp.substring(0, 64) : realIp;
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "-" : remote;
    }
}
