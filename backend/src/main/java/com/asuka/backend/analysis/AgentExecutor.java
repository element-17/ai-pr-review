package com.asuka.backend.analysis;

import com.asuka.backend.exception.AgentException;
import com.asuka.backend.properties.AiProperties;
import com.asuka.backend.integration.ai.ModelClient;
import com.asuka.backend.exception.ModelException;
import com.asuka.backend.integration.ai.ModelMessage;
import com.asuka.backend.integration.ai.ModelRequest;
import com.asuka.backend.integration.ai.ModelResponse;
import com.asuka.backend.integration.ai.ModelTool;
import com.asuka.backend.integration.ai.ToolCall;
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
        List<ModelMessage> messages = new ArrayList<>();
        messages.add(ModelMessage.system(systemPrompt));
        messages.add(ModelMessage.user(userPrompt));

        int maxTurns = Math.max(1, aiProperties.getMaxTurns());
        int maxToolCalls = Math.max(1, aiProperties.getMaxToolCalls());
        int turns = 0;
        int toolCalls = 0;
        boolean reminded = false;

        while (turns < maxTurns) {
            ModelRequest request = new ModelRequest();
            request.setModel(aiProperties.getDeepseek().getModel());
            request.setMessages(new ArrayList<>(messages));
            request.setTools(tools);
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
                if (!reminded) {
                    messages.add(ModelMessage.assistant(message.getContent(), null));
                    messages.add(ModelMessage.system("You must call the required submit tool now."));
                    reminded = true;
                    continue;
                }
                throw new AgentException("模型未调用提交工具");
            }

            messages.add(ModelMessage.assistant(message.getContent(), calls));
            for (ToolCall call : calls) {
                String name = call.getFunction() == null ? null : call.getFunction().getName();
                Map<String, Object> arguments = parseArguments(call);
                if (terminalTools.contains(name)) {
                    log.info("agent 完成：stage={}, turns={}, toolCalls={}", context.getStage(), turns, toolCalls);
                    return new AgentSubmission(name, arguments, turns, toolCalls);
                }

                toolCalls++;
                if (toolCalls > maxToolCalls) {
                    throw new AgentException("工具调用次数超限");
                }
                String result = toolRegistry.execute(name, arguments, context);
                messages.add(ModelMessage.tool(call.getId(), result));
            }
        }
        throw new AgentException("模型轮次超限");
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
