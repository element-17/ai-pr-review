package com.asuka.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 异步任务执行参数。
 */
@Data
@Component
@ConfigurationProperties(prefix = "pr.task")
public class TaskProperties {

    /** 固定 worker 数 */
    private int workerCount = 2;

    /** 执行队列容量 */
    private int queueCapacity = 100;

    /** 排队任务扫描间隔（毫秒） */
    private long dispatchIntervalMs = 5000;

    /** 仅 stub pipeline 使用的模拟耗时，便于验证取消 */
    private long stubDelayMs = 0;
}
