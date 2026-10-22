package com.asuka.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 分析任务线程池。
 *
 * <p>固定 worker 数，队列有界；真正的并发安全由数据库条件更新 claim 保证。
 */
@Configuration
public class TaskExecutorConfig {

    @Bean(name = "analysisTaskExecutor")
    public ThreadPoolTaskExecutor analysisTaskExecutor(TaskProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, properties.getWorkerCount()));
        executor.setMaxPoolSize(Math.max(1, properties.getWorkerCount()));
        executor.setQueueCapacity(Math.max(1, properties.getQueueCapacity()));
        executor.setThreadNamePrefix("analysis-worker-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
