package com.asuka.backend.service;

import com.asuka.backend.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 任务创建/重试的幂等键。
 *
 * <p>Redis 存请求哈希和第一次响应，TTL 10 分钟；
 * 同 key 不同请求体返回 409，同 key 同请求体返回第一次结果。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate stringRedisTemplate;

    public String lookup(String scope, String identity, String key, String requestHash) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        try {
            String raw = stringRedisTemplate.opsForValue().get(redisKey(scope, identity, key));
            if (!StringUtils.hasText(raw)) {
                return null;
            }
            Record record = OBJECT_MAPPER.readValue(raw, Record.class);
            if (!requestHash.equals(record.getRequestHash())) {
                throw new BusinessException(409, "幂等键对应了不同的请求体");
            }
            return record.getResponse();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("读取幂等键失败，按首次请求处理：{}", scope);
            return null;
        }
    }

    public void store(String scope, String identity, String key, String requestHash, String responseJson) {
        if (!StringUtils.hasText(key)) {
            return;
        }
        try {
            Record record = new Record();
            record.setRequestHash(requestHash);
            record.setResponse(responseJson);
            stringRedisTemplate.opsForValue().set(redisKey(scope, identity, key),
                    OBJECT_MAPPER.writeValueAsString(record), TTL);
        } catch (Exception ex) {
            log.warn("写入幂等键失败：{}", scope);
        }
    }

    private String redisKey(String scope, String identity, String key) {
        return "idem:" + scope + ":" + identity + ":" + key;
    }

    @Data
    public static class Record {
        private String requestHash;
        private String response;
    }
}
