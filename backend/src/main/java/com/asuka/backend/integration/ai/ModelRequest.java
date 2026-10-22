package com.asuka.backend.integration.ai;

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

    @JsonProperty("tool_choice")
    private String toolChoice = "auto";

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
