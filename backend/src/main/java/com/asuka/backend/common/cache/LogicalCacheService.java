package com.asuka.backend.common.cache;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 逻辑过期缓存服务。
 *
 * <p>值以 {@link CacheEnvelope} 存储，物理 TTL 由 Redis cache 配置负责，
 * 逻辑过期时间放在 envelope 内。逻辑过期后先返回旧值，再通过 Redis 锁
 * 保证只有一个实例提交异步刷新，避免缓存击穿。
 */
@Service
public class LogicalCacheService {

    private static final Logger log = LoggerFactory.getLogger(LogicalCacheService.class);

    private static final String LOCK_KEY_PREFIX = "cache:lock:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final int MISS_RETRY_TIMES = 3;
    private static final long MISS_RETRY_INTERVAL_MILLIS = 50L;
    private static final int SCAN_COUNT = 1000;
    private static final int DELETE_BATCH_SIZE = 500;

    private final CacheManager cacheManager;
    private final StringRedisTemplate stringRedisTemplate;
    private final ThreadPoolTaskExecutor cacheRefreshExecutor;

    public LogicalCacheService(CacheManager cacheManager,
                               StringRedisTemplate stringRedisTemplate,
                               @Qualifier("cacheRefreshExecutor")
                               ThreadPoolTaskExecutor cacheRefreshExecutor) {
        this.cacheManager = cacheManager;
        this.stringRedisTemplate = stringRedisTemplate;
        this.cacheRefreshExecutor = cacheRefreshExecutor;
    }

    /**
     * 读取逻辑过期缓存；未命中时同步加载，逻辑过期时异步刷新并返回旧值。
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String cacheName, String key, long logicalTtlSeconds, Supplier<T> loader) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return loader.get();
        }

        CacheEnvelope envelope = readEnvelope(cache, key);
        if (envelope != null) {
            if (envelope.getExpireAt() > System.currentTimeMillis()) {
                return (T) envelope.getData();
            }
            refreshAsync(cacheName, cache, key, logicalTtlSeconds, loader);
            return (T) envelope.getData();
        }

        String lockKey = lockKey(cacheName, key);
        if (tryLock(lockKey)) {
            try {
                return loadAndPut(cache, key, logicalTtlSeconds, loader);
            } finally {
                unlock(lockKey);
            }
        }

        // 其他实例正在加载，短暂等待后重试读取，避免直接压到数据库
        for (int i = 0; i < MISS_RETRY_TIMES; i++) {
            sleepQuietly();
            CacheEnvelope loaded = readEnvelope(cache, key);
            if (loaded != null) {
                return (T) loaded.getData();
            }
        }
        return loadAndPut(cache, key, logicalTtlSeconds, loader);
    }

    /**
     * 删除单个缓存项。
     */
    public void evict(String cacheName, String key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
        }
    }

    /**
     * 按 key 前缀批量删除缓存项，使用 SCAN 避免阻塞 Redis。
     */
    public void evictByPrefix(String cacheName, String keyPrefix) {
        String pattern = cacheName + ":" + keyPrefix + "*";
        List<String> keys = new ArrayList<>();
        try (Cursor<String> cursor = stringRedisTemplate.scan(
                ScanOptions.scanOptions().match(pattern).count(SCAN_COUNT).build())) {
            cursor.forEachRemaining(keys::add);
        }
        for (int i = 0; i < keys.size(); i += DELETE_BATCH_SIZE) {
            int end = Math.min(i + DELETE_BATCH_SIZE, keys.size());
            stringRedisTemplate.delete(keys.subList(i, end));
        }
    }

    private <T> T loadAndPut(Cache cache, String key, long logicalTtlSeconds, Supplier<T> loader) {
        T data = loader.get();
        cache.put(key, new CacheEnvelope(data, nextExpireAt(logicalTtlSeconds)));
        return data;
    }

    private <T> void refreshAsync(String cacheName, Cache cache, String key,
                                  long logicalTtlSeconds, Supplier<T> loader) {
        String lockKey = lockKey(cacheName, key);
        if (!tryLock(lockKey)) {
            return;
        }
        try {
            cacheRefreshExecutor.execute(() -> {
                try {
                    T data = loader.get();
                    cache.put(key, new CacheEnvelope(data, nextExpireAt(logicalTtlSeconds)));
                } catch (Exception ex) {
                    log.warn("缓存异步刷新失败 cache={} key={} reason={}",
                            cacheName, key, ex.getMessage());
                } finally {
                    unlock(lockKey);
                }
            });
        } catch (TaskRejectedException ex) {
            unlock(lockKey);
            log.warn("缓存刷新线程池已满，跳过本次刷新 cache={} key={}", cacheName, key);
        }
    }

    private CacheEnvelope readEnvelope(Cache cache, String key) {
        Cache.ValueWrapper wrapper = cache.get(key);
        if (wrapper == null) {
            return null;
        }
        Object value = wrapper.get();
        return value instanceof CacheEnvelope envelope ? envelope : null;
    }

    /**
     * 逻辑过期时间增加 0~20% 抖动，避免同一批任务同时过期。
     */
    private long nextExpireAt(long logicalTtlSeconds) {
        long ttlMillis = logicalTtlSeconds * 1000L;
        long jitterMillis = ThreadLocalRandom.current().nextLong(ttlMillis / 5 + 1);
        return System.currentTimeMillis() + ttlMillis + jitterMillis;
    }

    private String lockKey(String cacheName, String key) {
        return LOCK_KEY_PREFIX + cacheName + ":" + key;
    }

    private boolean tryLock(String lockKey) {
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
        return Boolean.TRUE.equals(acquired);
    }

    private void unlock(String lockKey) {
        try {
            stringRedisTemplate.delete(lockKey);
        } catch (Exception ex) {
            log.warn("释放缓存刷新锁失败 lock={} reason={}", lockKey, ex.getMessage());
        }
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(MISS_RETRY_INTERVAL_MILLIS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 缓存信封：业务数据允许为 null，用于空值缓存。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CacheEnvelope {

        private Object data;

        private long expireAt;
    }
}
