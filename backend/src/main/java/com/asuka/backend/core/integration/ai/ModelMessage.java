package com.asuka.backend.core.integration.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * OpenAI 兼容消息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModelMessage {

    private String role;

    private String content;

    @JsonProperty("tool_call_id")
    private String toolCallId;

    @JsonProperty("tool_calls")
    private List<ToolCall> toolCalls;

    public static ModelMessage system(String content) {
        return new ModelMessage("system", content, null, null);
    }

    public static ModelMessage user(String content) {
        return new ModelMessage("user", content, null, null);
    }

    public static ModelMessage tool(String toolCallId, String content) {
        return new ModelMessage("tool", content, toolCallId, null);
    }

    public static ModelMessage assistant(String content, List<ToolCall> toolCalls) {
        return new ModelMessage("assistant", content, null, toolCalls);
    }
}
