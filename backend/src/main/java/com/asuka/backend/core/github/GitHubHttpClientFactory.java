package com.asuka.backend.core.github;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.util.StringUtils;

/** 仅供 GitHub 客户端使用的可选 HTTP CONNECT 代理。 */
public final class GitHubHttpClientFactory {
    private GitHubHttpClientFactory() {}

    public static HttpClient create(int connectTimeoutMs, String proxyHost, int proxyPort) {
        var builder = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectTimeoutMs));
        if (StringUtils.hasText(proxyHost)) {
            if (proxyPort < 1 || proxyPort > 65535) throw new IllegalArgumentException("Invalid GitHub proxy port");
            builder.proxy(ProxySelector.of(InetSocketAddress.createUnresolved(proxyHost.trim(), proxyPort)));
        }
        return builder.build();
    }
}
