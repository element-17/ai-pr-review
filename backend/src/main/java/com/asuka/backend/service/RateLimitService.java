package com.asuka.backend.service;

import com.asuka.backend.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

/**
 * 轻量 Redis 限流。
 *
 * <p>只覆盖接口文档要求的 PR 预览和游客创建任务；
 * Redis 不可用时放行，避免限流组件拖垮整个预览链路。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final StringRedisTemplate stringRedisTemplate;

    public void checkPreview() {
        check("rate:preview:" + clientIp(), 30, Duration.ofMinutes(1),
                "PR 预览过于频繁，请稍后重试");
    }

    public void checkGuestTask() {
        check("rate:guest:task:" + clientIp(), 5, Duration.ofHours(1),
                "游客创建任务过于频繁，请稍后重试");
    }

    private void check(String key, int limit, Duration window, String message) {
        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                stringRedisTemplate.expire(key, window);
            }
            if (count != null && count > limit) {
                throw new BusinessException(429, message);
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("限流组件不可用，本次放行：{}", key);
        }
    }

    public String clientIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return "unknown";
        }
        HttpServletRequest request = attributes.getRequest();
        String ip = request.getRemoteAddr();
        return StringUtils.hasText(ip) ? ip : "unknown";
    }
}
