package com.asuka.backend.core.agent.ai;

import com.asuka.backend.common.exception.ModelException;
import com.asuka.backend.common.properties.AiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 无 DeepSeek Key 时使用的确定性 fake client。
 *
 * <p>只在 pr.ai.provider=fake 时启用；用于本地联调和自动化测试 agent 工具循环。
 * 第一次返回 read_file 工具调用，收到工具结果后调用 submit_*。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pr.ai.provider", havingValue = "fake")
public class FakeModelClient implements ModelClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AiProperties aiProperties;

    @Override
    public ModelResponse chat(ModelRequest request) {
        String stage = request.getStage();
        Map<String, Object> context = request.getContext() == null ? Map.of() : request.getContext();
        List<Map<String, Object>> files = castFiles(context.get("files"));
        boolean hasToolResult = request.getMessages().stream()
                .anyMatch(message -> "tool".equals(message.getRole()));

        String toolName;
        Map<String, Object> arguments;
        if (!hasToolResult && !files.isEmpty()) {
            toolName = "read_file";
            arguments = new LinkedHashMap<>();
            arguments.put("path", files.get(0).get("path"));
            arguments.put("startLine", 1);
            arguments.put("endLine", 40);
        } else if ("summary".equals(stage)) {
            toolName = "submit_summary";
            arguments = summaryArguments(files);
        } else {
            toolName = "submit_issues";
            arguments = issueArguments(files);
        }

        ToolCall call = new ToolCall(
                "fake-" + System.nanoTime(),
                "function",
                new ToolCall.Function(toolName, toJson(arguments)));
        ModelResponse.Message message = new ModelResponse.Message();
        message.setRole("assistant");
        message.setToolCalls(List.of(call));
        ModelResponse.Choice choice = new ModelResponse.Choice();
        choice.setMessage(message);
        choice.setFinishReason("tool_calls");
        ModelResponse response = new ModelResponse();
        response.setChoices(List.of(choice));
        ModelResponse.Usage usage = new ModelResponse.Usage();
        usage.setPromptTokens(0);
        usage.setCompletionTokens(0);
        usage.setTotalTokens(0);
        response.setUsage(usage);
        return response;
    }

    private Map<String, Object> summaryArguments(List<Map<String, Object>> files) {
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (Map<String, Object> file : files) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("path", file.get("path"));
            item.put("summary", "Fake file summary for " + file.get("path"));
            item.put("priorityReason", "Fake priority reason");
            summaries.add(item);
        }
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("purpose", "Fake summary purpose");
        arguments.put("changes", "Fake summary changes");
        arguments.put("impacts", "Fake summary impacts");
        arguments.put("files", summaries);
        return arguments;
    }

    private Map<String, Object> issueArguments(List<Map<String, Object>> files) {
        List<Map<String, Object>> issues = new ArrayList<>();
        for (Map<String, Object> file : files) {
            Object line = file.get("firstAddedLine");
            if (line == null) {
                continue;
            }
            Map<String, Object> issue = new LinkedHashMap<>();
            issue.put("title", "Fake issue in " + file.get("path"));
            issue.put("category", 2);
            issue.put("severity", 3);
            issue.put("confidence", 80);
            issue.put("filePath", file.get("path"));
            issue.put("startLine", line);
            issue.put("endLine", line);
            issue.put("triggerCondition", "Fake trigger condition");
            issue.put("impact", "Fake impact");
            issue.put("evidence", "Fake evidence");
            issue.put("suggestion", "Fake suggestion");
            issues.add(issue);
        }

        Map<String, Object> crossFile = new LinkedHashMap<>();
        crossFile.put("title", "Fake cross-file issue");
        crossFile.put("category", 4);
        crossFile.put("severity", 3);
        crossFile.put("confidence", 55);
        crossFile.put("filePath", null);
        crossFile.put("triggerCondition", "Fake cross-file trigger");
        crossFile.put("impact", "Fake cross-file impact");
        crossFile.put("evidence", null);
        crossFile.put("suggestion", "Fake cross-file suggestion");
        issues.add(crossFile);

        if (aiProperties.isFakeInvalidIssue()) {
            Map<String, Object> invalid = new LinkedHashMap<>();
            invalid.put("title", "Fake invalid file issue");
            invalid.put("category", 1);
            invalid.put("severity", 2);
            invalid.put("confidence", 70);
            invalid.put("filePath", "missing-file.ts");
            invalid.put("startLine", 10);
            invalid.put("endLine", 10);
            invalid.put("triggerCondition", "Fake trigger");
            invalid.put("impact", "Fake impact");
            invalid.put("evidence", "Fake evidence");
            invalid.put("suggestion", "Fake suggestion");
            issues.add(invalid);
        }

        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("issues", issues);
        return arguments;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castFiles(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add((Map<String, Object>) map);
                }
            }
            return result;
        }
        return List.of();
    }

    private String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            throw new ModelException(ModelException.Kind.FORMAT, "Fake client 参数序列化失败", ex);
        }
    }
}
