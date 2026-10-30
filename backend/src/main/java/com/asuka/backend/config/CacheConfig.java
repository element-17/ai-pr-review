package com.asuka.backend.config;

import com.asuka.backend.common.cache.CacheNames;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Redis 缓存配置。
 *
 * <p>key 统一使用 {@code cacheName:} 前缀；value 使用带类型信息的 JSON，
 * 并注册 JavaTimeModule 以支持 LocalDateTime。物理 TTL 在基础值上增加随机抖动，
 * 避免大量 key 同时过期造成缓存雪崩。
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private static final Duration USER_SETTING_TTL = Duration.ofMinutes(10);
    private static final long USER_SETTING_TTL_JITTER_SECONDS = 120;

    private static final Duration TASK_TTL = Duration.ofMinutes(30);
    private static final long TASK_TTL_JITTER_SECONDS = 300;

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .computePrefixWith(name -> name + ":")
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(SerializationPair.fromSerializer(cacheValueSerializer()));

        Map<String, RedisCacheConfiguration> configurations = new HashMap<>();
        configurations.put(CacheNames.USER_SETTING,
                base.entryTtl(withJitter(USER_SETTING_TTL, USER_SETTING_TTL_JITTER_SECONDS)));
        configurations.put(CacheNames.USER_SETTING_CONFIG,
                base.entryTtl(withJitter(USER_SETTING_TTL, USER_SETTING_TTL_JITTER_SECONDS)));
        configurations.put(CacheNames.TASK_DETAIL,
                base.entryTtl(withJitter(TASK_TTL, TASK_TTL_JITTER_SECONDS)));
        configurations.put(CacheNames.TASK_REPORT,
                base.entryTtl(withJitter(TASK_TTL, TASK_TTL_JITTER_SECONDS)));
        configurations.put(CacheNames.TASK_FILES,
                base.entryTtl(withJitter(TASK_TTL, TASK_TTL_JITTER_SECONDS)));
        configurations.put(CacheNames.TASK_DIFF,
                base.entryTtl(withJitter(TASK_TTL, TASK_TTL_JITTER_SECONDS)));

        RedisCacheWriter writer = RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory);
        return new RedisCacheManager(writer,
                base.entryTtl(withJitter(TASK_TTL, TASK_TTL_JITTER_SECONDS)),
                true,
                configurations);
    }

    @Bean(name = "cacheRefreshExecutor")
    public ThreadPoolTaskExecutor cacheRefreshExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("cache-refresh-");
        executor.initialize();
        return executor;
    }

    /**
     * 每个 entry 的物理 TTL 在基础值上随机增加 0~jitterSeconds 秒。
     */
    private static RedisCacheWriter.TtlFunction withJitter(Duration base, long jitterSeconds) {
        return (key, value) -> base.plusSeconds(
                ThreadLocalRandom.current().nextLong(jitterSeconds + 1));
    }

    /**
     * 带类型信息的 JSON 序列化器，确保 Object 字段可以还原为具体 DTO。
     */
    private static RedisSerializer<Object> cacheValueSerializer() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return GenericJackson2JsonRedisSerializer.builder()
                .objectMapper(mapper)
                .defaultTyping(true)
                .registerNullValueSerializer(true)
                .build();
    }
}
