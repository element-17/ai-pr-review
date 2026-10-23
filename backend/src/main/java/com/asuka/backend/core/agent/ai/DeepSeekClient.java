package com.asuka.backend.core.agent.ai;

import com.asuka.backend.common.exception.ModelException;
import com.asuka.backend.common.properties.AiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * DeepSeek OpenAI 兼容客户端。
 *
 * <p>不引入 SDK；请求和响应都用独立 Jackson 2 解析。
 * 不记录 prompt、工具结果或模型原文。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "pr.ai.provider", havingValue = "deepseek", matchIfMissing = true)
public class DeepSeekClient implements ModelClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AiProperties.DeepSeek properties;
    private final RestClient restClient;

    public DeepSeekClient(AiProperties aiProperties) {
        this.properties = aiProperties.getDeepseek();
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public ModelResponse chat(ModelRequest request) {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new ModelException(ModelException.Kind.NOT_CONFIGURED, "DeepSeek API Key 未配置");
        }
        if (request.getModel() == null) {
            request.setModel(properties.getModel());
        }
        if (request.getMaxTokens() == null) {
            request.setMaxTokens(properties.getMaxTokens());
        }
        if (request.getTemperature() == null) {
            request.setTemperature(properties.getTemperature());
        }
        if (request.getThinking() == null) {
            request.setThinking(Map.of("type", "disabled"));
        }

        long started = System.currentTimeMillis();
        try {
            String body = OBJECT_MAPPER.writeValueAsString(request);
            String responseBody = restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            ModelResponse response = OBJECT_MAPPER.readValue(responseBody, ModelResponse.class);
            if (response.getUsage() != null) {
                log.info("DeepSeek 调用完成：prompt={}, completion={}, total={}, durationMs={}",
                        response.getUsage().getPromptTokens(),
                        response.getUsage().getCompletionTokens(),
                        response.getUsage().getTotalTokens(),
                        System.currentTimeMillis() - started);
            }
            return response;
        } catch (RestClientResponseException ex) {
            log.warn("DeepSeek 调用失败：HTTP {}, durationMs={}",
                    ex.getStatusCode().value(), System.currentTimeMillis() - started);
            throw new ModelException(ModelException.Kind.UPSTREAM,
                    "DeepSeek 调用失败：HTTP " + ex.getStatusCode().value());
        } catch (ResourceAccessException ex) {
            log.warn("DeepSeek 调用超时：{}, durationMs={}",
                    ex.getMessage(), System.currentTimeMillis() - started);
            throw new ModelException(ModelException.Kind.TIMEOUT, "DeepSeek 调用超时", ex);
        } catch (ModelException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("DeepSeek 响应解析失败：{}, durationMs={}",
                    ex.getClass().getSimpleName(), System.currentTimeMillis() - started);
            throw new ModelException(ModelException.Kind.FORMAT, "DeepSeek 响应解析失败", ex);
        }
    }
}
