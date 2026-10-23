package com.asuka.backend.service;

/**
 * 预览与游客任务的限流服务。
 */
public interface RateLimitService {

    void checkPreview();

    void checkGuestTask();

    String clientIp();
}
