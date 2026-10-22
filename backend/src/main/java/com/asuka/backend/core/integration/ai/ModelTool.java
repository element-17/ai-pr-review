package com.asuka.backend.core.integration.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 提供给模型的工具定义。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelTool {

    private String type = "function";

    private FunctionDef function;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionDef {
        private String name;
        private String description;
        private Map<String, Object> parameters;
    }
}
