package com.asuka.backend.core.agent;

import com.asuka.backend.common.exception.AgentException;
import com.asuka.backend.common.properties.AiProperties;
import com.asuka.backend.core.agent.ai.ModelClient;
import com.asuka.backend.common.exception.ModelException;
import com.asuka.backend.core.agent.ai.ModelMessage;
import com.asuka.backend.core.agent.ai.ModelRequest;
import com.asuka.backend.core.agent.ai.ModelResponse;
import com.asuka.backend.core.agent.ai.ModelTool;
import com.asuka.backend.core.agent.ai.ToolCall;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 简易 agent 工具循环。
 *
 * <p>模型可以调用只读工具补上下文，最终必须调用 submit_summary/submit_issues；
 * 达到轮次或工具次数上限、或提醒后仍不提交时抛 AgentException。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentExecutor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ModelClient modelClient;
    private final AgentToolRegistry toolRegistry;
    private final AiProperties aiProperties;

    public AgentSubmission run(AgentContext context,
                           String systemPrompt,
                           String userPrompt,
                           List<ModelTool> tools,
                           Set<String> terminalTools) {
        if (terminalTools == null || terminalTools.isEmpty()) {
            throw new AgentException("缺少提交工具");
        }
        String terminalTool = terminalTools.iterator().next();
        List<ModelTool> terminalOnly = tools.stream()
                .filter(tool -> tool.getFunction() != null && terminalTools.contains(tool.getFunction().getName()))
                .toList();
        if (terminalOnly.isEmpty()) {
            throw new AgentException("工具列表中缺少提交工具：" + terminalTool);
        }

        List<ModelMessage> messages = new ArrayList<>();
        messages.add(ModelMessage.system(systemPrompt));
        messages.add(ModelMessage.user(userPrompt));

        int maxTurns = Math.max(1, aiProperties.getMaxTurns());
        int maxToolCalls = Math.max(1, aiProperties.getMaxToolCalls());
        int turns = 0;
        int toolCalls = 0;
        boolean reminded = false;
        boolean forced = false;

        while (true) {
            boolean budgetExhausted = turns >= maxTurns || toolCalls >= maxToolCalls;
            if (budgetExhausted && !forced) {
                forced = true;
                messages.add(ModelMessage.system(forceSubmitPrompt(terminalTool, context.getStage())));
                log.warn("agent 预算耗尽，强制提交：stage={}, turns={}, toolCalls={}, terminal={}",
                        context.getStage(), turns, toolCalls, terminalTool);
            }

            ModelRequest request = new ModelRequest();
            request.setModel(aiProperties.getDeepseek().getModel());
            request.setMessages(new ArrayList<>(messages));
            // 最后一次机会只暴露提交工具，并用 tool_choice 强制模型调用它。
            request.setTools(forced ? terminalOnly : tools);
            if (forced) {
                request.setToolChoice(Map.of(
                        "type", "function",
                        "function", Map.of("name", terminalTool)
                ));
            }
            request.setMaxTokens(aiProperties.getDeepseek().getMaxTokens());
            request.setTemperature(aiProperties.getDeepseek().getTemperature());
            request.setStage(context.getStage());
            request.setContext(Map.of(
                    "stage", context.getStage(),
                    "files", context.getFiles(),
                    "english", context.isEnglish()
            ));

            ModelResponse response = modelClient.chat(request);
            turns++;
            if (response.getChoices() == null || response.getChoices().isEmpty()
                    || response.getChoices().get(0).getMessage() == null) {
                throw new ModelException(ModelException.Kind.FORMAT, "模型返回为空");
            }
            ModelResponse.Message message = response.getChoices().get(0).getMessage();
            List<ToolCall> calls = message.getToolCalls();
            if (calls == null || calls.isEmpty()) {
                if (forced) {
                    throw new AgentException("预算耗尽后模型仍未调用提交工具");
                }
                if (!reminded) {
                    messages.add(ModelMessage.assistant(message.getContent(), null));
                    messages.add(ModelMessage.system("You must call the required submit tool now."));
                    reminded = true;
                    continue;
                }
                forced = true;
                messages.add(ModelMessage.system(forceSubmitPrompt(terminalTool, context.getStage())));
                log.warn("agent 多次未调用提交工具，强制提交：stage={}, turns={}, toolCalls={}, terminal={}",
                        context.getStage(), turns, toolCalls, terminalTool);
                continue;
            }

            messages.add(ModelMessage.assistant(message.getContent(), calls));
            for (ToolCall call : calls) {
                String name = call.getFunction() == null ? null : call.getFunction().getName();
                Map<String, Object> arguments = parseArguments(call);
                if (terminalTools.contains(name)) {
                    log.info("agent 完成：stage={}, turns={}, toolCalls={}", context.getStage(), turns, toolCalls);
                    return new AgentSubmission(name, arguments, turns, toolCalls);
                }
                if (forced) {
                    throw new AgentException("预算耗尽后模型调用了非提交工具：" + name);
                }
                if (toolCalls >= maxToolCalls) {
                    messages.add(ModelMessage.tool(call.getId(),
                            "Skipped: tool budget exhausted. Submit your best-effort result now."));
                    continue;
                }
                toolCalls++;
                String result = toolRegistry.execute(name, arguments, context);
                messages.add(ModelMessage.tool(call.getId(), result));
            }
        }
    }

    private String forceSubmitPrompt(String terminalTool, String stage) {
        String limit = "issues".equals(stage)
                ? " Return at most " + AgentToolDefinitions.MAX_ISSUES_PER_BATCH
                + " issues for this batch and keep each field concise."
                : "";
        return "The tool/turn budget is exhausted. Do not call any other tool. "
                + "You must call " + terminalTool
                + " now with the best-effort structured result using the information already provided."
                + limit;
    }

    private Map<String, Object> parseArguments(ToolCall call) {
        if (call.getFunction() == null || !StringUtils.hasText(call.getFunction().getArguments())) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(call.getFunction().getArguments(),
                    new TypeReference<Map<String, Object>>() {
                    });
        } catch (Exception ex) {
            throw new AgentException("工具参数不是合法 JSON");
        }
    }
}
