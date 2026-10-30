package com.asuka.backend.core.agent.ai;

import com.asuka.backend.common.exception.ModelException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anthropic Messages 兼容客户端。
 *
 * <p>把内部的 OpenAI 风格消息/工具调用映射为 Anthropic 的 system、tool_use 和 tool_result；
 * 响应再统一转换回 ModelResponse，供现有 agent 工具循环复用。
 */
@Slf4j
@Component
public class AnthropicMessagesModelClient implements ProviderModelClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_RETRIES = 2;
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    @Override
    public ModelResponse chat(ModelRequest request, ModelRuntimeConfig config) {
        if (config == null || !StringUtils.hasText(config.getApiKey())) {
            throw new ModelException(ModelException.Kind.NOT_CONFIGURED, "模型 API Key 未配置");
        }
        String body;
        try {
            body = OBJECT_MAPPER.writeValueAsString(buildBody(request, config));
        } catch (ModelException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ModelException(ModelException.Kind.FORMAT, "Anthropic 请求构造失败", ex);
        }

        String url = stripTrailingSlash(config.getBaseUrl()) + "/messages";
        RestClient restClient = restClient(config);
        long started = System.currentTimeMillis();
        int attempt = 0;
        while (true) {
            try {
                String responseBody = restClient.post()
                        .uri(url)
                        .header("x-api-key", config.getApiKey())
                        .header("anthropic-version", ANTHROPIC_VERSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(String.class);
                AnthropicResponse response = OBJECT_MAPPER.readValue(responseBody, AnthropicResponse.class);
                ModelResponse converted = convert(response);
                logUsage(config, converted, System.currentTimeMillis() - started);
                return converted;
            } catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                if (retryable(status) && attempt < MAX_RETRIES) {
                    sleep(retryDelay(ex, attempt));
                    attempt++;
                    continue;
                }
                log.warn("Anthropic 模型调用失败：status={}, model={}, durationMs={}",
                        status, config.getModelId(), System.currentTimeMillis() - started);
                throw mapStatus(status);
            } catch (ResourceAccessException ex) {
                if (attempt < MAX_RETRIES) {
                    sleep(500L * (attempt + 1));
                    attempt++;
                    continue;
                }
                log.warn("Anthropic 模型调用超时：model={}, durationMs={}",
                        config.getModelId(), System.currentTimeMillis() - started);
                throw new ModelException(ModelException.Kind.TIMEOUT, "模型调用超时", ex);
            } catch (ModelException ex) {
                throw ex;
            } catch (Exception ex) {
                log.warn("Anthropic 模型响应解析失败：model={}, durationMs={}",
                        config.getModelId(), System.currentTimeMillis() - started);
                throw new ModelException(ModelException.Kind.FORMAT, "模型响应解析失败", ex);
            }
        }
    }

    private Map<String, Object> buildBody(ModelRequest request, ModelRuntimeConfig config) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", StringUtils.hasText(request.getModel()) ? request.getModel() : config.getModelId());
        body.put("max_tokens", request.getMaxTokens() != null
                ? request.getMaxTokens()
                : (config.getMaxOutputTokens() == null
                ? ModelDefaults.ANTHROPIC_MAX_OUTPUT_TOKENS : config.getMaxOutputTokens()));

        String system = request.getMessages().stream()
                .filter(message -> "system".equals(message.getRole()))
                .map(ModelMessage::getContent)
                .filter(StringUtils::hasText)
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse(null);
        if (StringUtils.hasText(system)) {
            body.put("system", system);
        }

        Double temperature = request.getTemperature();
        if (temperature == null && config.getTemperature() != null) {
            temperature = config.getTemperature().doubleValue();
        }
        if (temperature != null) {
            body.put("temperature", temperature);
        }

        body.put("messages", convertMessages(request.getMessages()));
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            body.put("tools", convertTools(request.getTools()));
            body.put("tool_choice", convertToolChoice(request.getToolChoice()));
        }
        return body;
    }

    /**
     * OpenAI 风格的 tool 消息要合并成 Anthropic 的 user.tool_result content block。
     */
    private List<Map<String, Object>> convertMessages(List<ModelMessage> messages) {
        List<Map<String, Object>> result = new ArrayList<>();
        List<Map<String, Object>> pendingToolResults = new ArrayList<>();
        for (ModelMessage message : messages) {
            if (message == null || "system".equals(message.getRole())) {
                continue;
            }
            if ("tool".equals(message.getRole())) {
                Map<String, Object> block = new LinkedHashMap<>();
                block.put("type", "tool_result");
                block.put("tool_use_id", message.getToolCallId());
                block.put("content", message.getContent() == null ? "" : message.getContent());
                pendingToolResults.add(block);
                continue;
            }
            flushToolResults(result, pendingToolResults);
            if ("assistant".equals(message.getRole())) {
                List<Map<String, Object>> blocks = new ArrayList<>();
                if (StringUtils.hasText(message.getContent())) {
                    blocks.add(textBlock(message.getContent()));
                }
                if (message.getToolCalls() != null) {
                    for (ToolCall call : message.getToolCalls()) {
                        if (call == null || call.getFunction() == null) {
                            continue;
                        }
                        Map<String, Object> block = new LinkedHashMap<>();
                        block.put("type", "tool_use");
                        block.put("id", call.getId());
                        block.put("name", call.getFunction().getName());
                        block.put("input", parseArguments(call.getFunction().getArguments()));
                        blocks.add(block);
                    }
                }
                if (!blocks.isEmpty()) {
                    result.add(Map.of("role", "assistant", "content", blocks));
                }
            } else {
                List<Map<String, Object>> blocks = new ArrayList<>();
                if (StringUtils.hasText(message.getContent())) {
                    blocks.add(textBlock(message.getContent()));
                }
                if (!blocks.isEmpty()) {
                    result.add(Map.of("role", "user", "content", blocks));
                }
            }
        }
        flushToolResults(result, pendingToolResults);
        return result;
    }

    private void flushToolResults(List<Map<String, Object>> result, List<Map<String, Object>> pending) {
        if (!pending.isEmpty()) {
            result.add(Map.of("role", "user", "content", new ArrayList<>(pending)));
            pending.clear();
        }
    }

    private Map<String, Object> textBlock(String text) {
        return Map.of("type", "text", "text", text);
    }

    private Object parseArguments(String arguments) {
        if (!StringUtils.hasText(arguments)) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(arguments, Map.class);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private List<Map<String, Object>> convertTools(List<ModelTool> tools) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ModelTool tool : tools) {
            if (tool == null || tool.getFunction() == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", tool.getFunction().getName());
            item.put("description", tool.getFunction().getDescription());
            item.put("input_schema", tool.getFunction().getParameters() == null
                    ? Map.of("type", "object", "properties", Map.of())
                    : tool.getFunction().getParameters());
            result.add(item);
        }
        return result;
    }

    private Object convertToolChoice(Object toolChoice) {
        if (toolChoice instanceof Map<?, ?> map) {
            Object function = map.get("function");
            if (function instanceof Map<?, ?> functionMap && functionMap.get("name") != null) {
                return Map.of("type", "tool", "name", functionMap.get("name").toString());
            }
        }
        if (toolChoice instanceof String value) {
            if ("any".equalsIgnoreCase(value) || "required".equalsIgnoreCase(value)) {
                return Map.of("type", "any");
            }
            if ("none".equalsIgnoreCase(value)) {
                return Map.of("type", "none");
            }
        }
        return Map.of("type", "auto");
    }

    private ModelResponse convert(AnthropicResponse response) {
        if (response == null || response.getContent() == null || response.getContent().isEmpty()) {
            throw new ModelException(ModelException.Kind.FORMAT, "Anthropic 响应为空");
        }
        StringBuilder text = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();
        try {
            for (AnthropicResponse.Content content : response.getContent()) {
                if (content == null) {
                    continue;
                }
                if ("text".equals(content.getType()) && content.getText() != null) {
                    text.append(content.getText());
                } else if ("tool_use".equals(content.getType())) {
                    String arguments = content.getInput() == null
                            ? "{}" : OBJECT_MAPPER.writeValueAsString(content.getInput());
                    toolCalls.add(new ToolCall(content.getId(), "function",
                            new ToolCall.Function(content.getName(), arguments)));
                }
            }
        } catch (Exception ex) {
            throw new ModelException(ModelException.Kind.FORMAT, "Anthropic 工具调用解析失败", ex);
        }
        if (text.length() == 0 && toolCalls.isEmpty()) {
            throw new ModelException(ModelException.Kind.FORMAT, "Anthropic 响应没有可用内容");
        }

        ModelResponse.Message message = new ModelResponse.Message();
        message.setRole("assistant");
        message.setContent(text.length() == 0 ? null : text.toString());
        message.setToolCalls(toolCalls.isEmpty() ? null : toolCalls);

        ModelResponse.Choice choice = new ModelResponse.Choice();
        choice.setMessage(message);
        choice.setFinishReason(mapStopReason(response.getStopReason()));

        ModelResponse result = new ModelResponse();
        result.setChoices(List.of(choice));
        if (response.getUsage() != null) {
            ModelResponse.Usage usage = new ModelResponse.Usage();
            int input = response.getUsage().getInputTokens() == null ? 0 : response.getUsage().getInputTokens();
            int output = response.getUsage().getOutputTokens() == null ? 0 : response.getUsage().getOutputTokens();
            usage.setPromptTokens(input);
            usage.setCompletionTokens(output);
            usage.setTotalTokens(input + output);
            result.setUsage(usage);
        }
        return result;
    }

    private String mapStopReason(String stopReason) {
        if ("tool_use".equals(stopReason)) {
            return "tool_calls";
        }
        if ("max_tokens".equals(stopReason)) {
            return "length";
        }
        return "stop";
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
        if (status == 400 || status == 422) {
            return new ModelException(ModelException.Kind.FORMAT, "模型请求格式不兼容");
        }
        return new ModelException(ModelException.Kind.UPSTREAM, "模型服务返回错误");
    }

    private void logUsage(ModelRuntimeConfig config, ModelResponse response, long durationMs) {
        if (response.getUsage() == null) {
            log.info("Anthropic 调用完成：model={}, durationMs={}", config.getModelId(), durationMs);
            return;
        }
        log.info("Anthropic 调用完成：model={}, prompt={}, completion={}, total={}, durationMs={}",
                config.getModelId(),
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

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class AnthropicResponse {
        private List<Content> content;

        @JsonProperty("stop_reason")
        private String stopReason;

        private Usage usage;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        static class Content {
            private String type;
            private String text;
            private String id;
            private String name;
            private JsonNode input;
        }

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        static class Usage {
            @JsonProperty("input_tokens")
            private Integer inputTokens;

            @JsonProperty("output_tokens")
            private Integer outputTokens;
        }
    }
}
