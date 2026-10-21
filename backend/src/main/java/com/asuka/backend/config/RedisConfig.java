package com.asuka.backend.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.protocol.ProtocolVersion;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis / Lettuce 兼容配置。
 *
 * <p>本地 Redis 3.0.504 不支持 Redis 7.2 才引入的 CLIENT SETINFO 握手命令，
 * Lettuce 6.8 默认会发送该命令，导致连接时报 "ERR syntax error"。
 * 这里强制使用 RESP2 协议，兼容旧版 Redis；生产环境升级到 Redis 7+ 后可移除。
 */
@Configuration
public class RedisConfig {

    @Bean
    public LettuceClientConfigurationBuilderCustomizer lettuceClientConfigurationBuilderCustomizer() {
        return builder -> builder.clientOptions(
                ClientOptions.builder()
                        .protocolVersion(ProtocolVersion.RESP2)
                        .build()
        );
    }
}
