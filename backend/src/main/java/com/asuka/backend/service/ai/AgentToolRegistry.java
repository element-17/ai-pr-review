package com.asuka.backend.service.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 只读工具执行器。
 *
 * <p>工具只读仓库文件，不执行 shell、不访问任意 URL、不写 GitHub。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentToolRegistry {

    private static final int MAX_TOOL_RESULT_CHARS = 20_000;

    public String execute(String name, Map<String, Object> arguments, AgentRunContext context) {
        try {
            String result = switch (name) {
                case "read_file" -> context.getContextBuilder().readFile(
                        stringArg(arguments, "path"),
                        integerArg(arguments, "startLine"),
                        integerArg(arguments, "endLine"));
                case "find_callers" -> context.getContextBuilder().findCallers(
                        stringArg(arguments, "symbol"),
                        stringArg(arguments, "path"));
                case "read_tests" -> context.getContextBuilder().readTests(
                        stringArg(arguments, "path"));
                default -> "{\"error\":\"unknown tool\"}";
            };
            if (!StringUtils.hasText(result)) {
                result = "(empty)";
            }
            if (result.length() > MAX_TOOL_RESULT_CHARS) {
                result = result.substring(0, MAX_TOOL_RESULT_CHARS);
            }
            return "<tool_result>\n" + result + "\n</tool_result>";
        } catch (Exception ex) {
            log.debug("agent 工具执行失败：{}", name);
            return "{\"error\":\"tool execution failed\"}";
        }
    }

    private String stringArg(Map<String, Object> arguments, String key) {
        Object value = arguments == null ? null : arguments.get(key);
        return value == null ? null : value.toString();
    }

    private Integer integerArg(Map<String, Object> arguments, String key) {
        Object value = arguments == null ? null : arguments.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
