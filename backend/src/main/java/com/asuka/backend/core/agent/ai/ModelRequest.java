package com.asuka.backend.core.agent.ai;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 Chat Completions 请求。
 */
@Data
public class ModelRequest {

    private String model;

    private List<ModelMessage> messages;

    private List<ModelTool> tools;

    /** "auto" 或 OpenAI 兼容的强制函数对象 {"type":"function","function":{"name":"..."}} */
    @JsonProperty("tool_choice")
    private Object toolChoice = "auto";

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    private Double temperature;

    private Map<String, Object> thinking;

    private boolean stream = false;

    /** 仅用于 fake client 和日志，不发送给模型 */
    @JsonIgnore
    private String stage;

    /** 仅用于 fake client，不发送给模型 */
    @JsonIgnore
    private Map<String, Object> context;
}
