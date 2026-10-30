package com.asuka.backend.core.agent.ai;

import com.asuka.backend.common.exception.ModelException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

/**
 * OpenAI Chat Completions 兼容客户端。
 *
 * <p>base URL 约定为 API 根地址（包含 /v1 之类的版本前缀），客户端追加 /chat/completions。
 * 只发送当前 provider 支持的 thinking 字段；日志不记录 prompt 或响应原文。
 */
@Slf4j
@Component
public class OpenAiChatModelClient implements ProviderModelClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_RETRIES = 2;

    @Override
    public ModelResponse chat(ModelRequest request, ModelRuntimeConfig config) {
        if (config == null || !StringUtils.hasText(config.getApiKey())) {
            throw new ModelException(ModelException.Kind.NOT_CONFIGURED, "模型 API Key 未配置");
        }
        applyDefaults(request, config);

        String url = stripTrailingSlash(config.getBaseUrl()) + "/chat/completions";
        RestClient restClient = restClient(config);
        long started = System.currentTimeMillis();
        int attempt = 0;
        while (true) {
            try {
                String body = OBJECT_MAPPER.writeValueAsString(request);
                String responseBody = restClient.post()
                        .uri(url)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.getApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(String.class);
                ModelResponse response = OBJECT_MAPPER.readValue(responseBody, ModelResponse.class);
                logUsage("OpenAI", config, response, System.currentTimeMillis() - started);
                return response;
            } catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                if (retryable(status) && attempt < MAX_RETRIES) {
                    sleep(retryDelay(ex, attempt));
                    attempt++;
                    continue;
                }
                log.warn("OpenAI 模型调用失败：status={}, model={}, durationMs={}",
                        status, config.getModelId(), System.currentTimeMillis() - started);
                throw mapStatus(status);
            } catch (ResourceAccessException ex) {
                if (attempt < MAX_RETRIES) {
                    sleep(500L * (attempt + 1));
                    attempt++;
                    continue;
                }
                log.warn("OpenAI 模型调用超时：model={}, durationMs={}",
                        config.getModelId(), System.currentTimeMillis() - started);
                throw new ModelException(ModelException.Kind.TIMEOUT, "模型调用超时", ex);
            } catch (ModelException ex) {
                throw ex;
            } catch (Exception ex) {
                log.warn("OpenAI 模型响应解析失败：model={}, durationMs={}",
                        config.getModelId(), System.currentTimeMillis() - started);
                throw new ModelException(ModelException.Kind.FORMAT, "模型响应解析失败", ex);
            }
        }
    }

    private RestClient restClient(ModelRuntimeConfig config) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1000, config.getConnectTimeoutMs())))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(Math.max(1000, config.getReadTimeoutMs())));
        return RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private void applyDefaults(ModelRequest request, ModelRuntimeConfig config) {
        if (!StringUtils.hasText(request.getModel())) {
            request.setModel(config.getModelId());
        }
        if (request.getMaxTokens() == null) {
            request.setMaxTokens(config.getMaxOutputTokens() == null
                    ? ModelDefaults.OPENAI_MAX_OUTPUT_TOKENS : config.getMaxOutputTokens());
        }
        if (request.getTemperature() == null) {
            request.setTemperature(config.getTemperature() == null
                    ? ModelDefaults.DEFAULT_TEMPERATURE : config.getTemperature().doubleValue());
        }
        if (config.isSendThinking()) {
            if (request.getThinking() == null) {
                request.setThinking(Map.of("type", "disabled"));
            }
        } else {
            request.setThinking(null);
        }
    }

    private boolean retryable(int status) {
        return status == 429 || status >= 500;
    }

    private long retryDelay(RestClientResponseException ex, int attempt) {
        String retryAfter = ex.getResponseHeaders() == null
                ? null : ex.getResponseHeaders().getFirst("Retry-After");
        if (StringUtils.hasText(retryAfter)) {
            try {
                return Math.min(Long.parseLong(retryAfter.trim()) * 1000, 5000);
            } catch (NumberFormatException ignored) {
                // 使用默认退避
            }
        }
        return 500L * (attempt + 1);
    }

    private ModelException mapStatus(int status) {
        if (status == 401 || status == 403) {
            return new ModelException(ModelException.Kind.AUTH, "模型鉴权失败，请检查 API Key");
        }
        if (status == 429) {
            return new ModelException(ModelException.Kind.RATE_LIMITED, "模型服务限流");
        }
        return new ModelException(ModelException.Kind.UPSTREAM, "模型服务返回错误");
    }

    private void logUsage(String provider, ModelRuntimeConfig config, ModelResponse response, long durationMs) {
        if (response.getUsage() == null) {
            log.info("{} 调用完成：model={}, durationMs={}", provider, config.getModelId(), durationMs);
            return;
        }
        log.info("{} 调用完成：model={}, prompt={}, completion={}, total={}, durationMs={}",
                provider, config.getModelId(),
                response.getUsage().getPromptTokens(),
                response.getUsage().getCompletionTokens(),
                response.getUsage().getTotalTokens(),
                durationMs);
    }

    private String stripTrailingSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ModelException(ModelException.Kind.UPSTREAM, "模型重试被中断", ex);
        }
    }
}
