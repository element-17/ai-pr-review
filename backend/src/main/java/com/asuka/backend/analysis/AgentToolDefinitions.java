package com.asuka.backend.analysis;

import com.asuka.backend.integration.ai.ModelTool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * agent 工具定义。
 *
 * <p>普通 tool schema，不依赖 DeepSeek strict/beta；所有参数后端再次校验。
 */
public final class AgentToolDefinitions {

    public static List<ModelTool> summaryTools() {
        List<ModelTool> tools = new ArrayList<>();
        tools.add(readFileTool());
        tools.add(submitSummaryTool());
        return tools;
    }

    public static List<ModelTool> issueTools() {
        List<ModelTool> tools = new ArrayList<>();
        tools.add(readFileTool());
        tools.add(findCallersTool());
        tools.add(readTestsTool());
        tools.add(submitIssuesTool());
        return tools;
    }

    private static ModelTool readFileTool() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("path", Map.of("type", "string", "description", "Repository-relative file path"));
        properties.put("startLine", Map.of("type", "integer", "description", "1-based start line, optional"));
        properties.put("endLine", Map.of("type", "integer", "description", "1-based end line, optional"));
        return tool("read_file", "Read a repository file at the PR head commit. Use it for function bodies, imports or tests.",
                objectSchema(properties, List.of("path")));
    }

    private static ModelTool findCallersTool() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("symbol", Map.of("type", "string", "description", "Function or symbol name"));
        properties.put("path", Map.of("type", "string", "description", "Changed file path, optional"));
        return tool("find_callers", "Find direct callers in changed or directly imported files. Do not invent callers.",
                objectSchema(properties, List.of("symbol")));
    }

    private static ModelTool readTestsTool() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("path", Map.of("type", "string", "description", "Changed file path, optional"));
        return tool("read_tests", "Read related test files from the same directory or __tests__ directory.",
                objectSchema(properties, List.of()));
    }

    private static ModelTool submitSummaryTool() {
        Map<String, Object> fileProps = new LinkedHashMap<>();
        fileProps.put("path", Map.of("type", "string"));
        fileProps.put("summary", Map.of("type", "string"));
        fileProps.put("priorityReason", Map.of("type", "string"));

        Map<String, Object> fileSchema = objectSchema(fileProps,
                List.of("path", "summary", "priorityReason"));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("purpose", Map.of("type", "string", "description", "PR purpose"));
        properties.put("changes", Map.of("type", "string", "description", "Main changes"));
        properties.put("impacts", Map.of("type", "string", "description", "Impacted modules"));
        properties.put("files", Map.of("type", "array", "items", fileSchema,
                "description", "Per-file summary and priority reason"));
        return tool("submit_summary", "Submit the PR-level summary and priority files. Call exactly once.",
                objectSchema(properties, List.of("purpose", "changes", "impacts", "files")));
    }

    private static ModelTool submitIssuesTool() {
        Map<String, Object> issueProps = new LinkedHashMap<>();
        issueProps.put("title", Map.of("type", "string"));
        issueProps.put("category", Map.of("type", "integer", "minimum", 1, "maximum", 7));
        issueProps.put("severity", Map.of("type", "integer", "minimum", 1, "maximum", 4));
        issueProps.put("confidence", Map.of("type", "integer", "minimum", 0, "maximum", 100));
        issueProps.put("filePath", Map.of("type", "string", "description", "Empty for report-level findings"));
        issueProps.put("startLine", Map.of("type", "integer", "description", "1-based new-file line, optional"));
        issueProps.put("endLine", Map.of("type", "integer", "description", "1-based new-file line, optional"));
        issueProps.put("triggerCondition", Map.of("type", "string"));
        issueProps.put("impact", Map.of("type", "string"));
        issueProps.put("evidence", Map.of("type", "string", "description", "Empty when evidence is insufficient"));
        issueProps.put("suggestion", Map.of("type", "string"));

        Map<String, Object> issueSchema = objectSchema(issueProps,
                List.of("title", "category", "severity", "confidence",
                        "triggerCondition", "impact", "suggestion"));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("issues", Map.of("type", "array", "items", issueSchema,
                "description", "Candidate issues for this batch"));
        return tool("submit_issues", "Submit candidate review issues for this batch. Call exactly once.",
                objectSchema(properties, List.of("issues")));
    }

    private static ModelTool tool(String name, String description, Map<String, Object> parameters) {
        return new ModelTool("function", new ModelTool.FunctionDef(name, description, parameters));
    }

    private static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    private AgentToolDefinitions() {
    }
}
