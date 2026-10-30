package com.asuka.backend.core.agent.ai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnthropicMessagesModelClientTest {

    @Test
    void mapsToolsAndToolResultsToMessagesApi() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/messages", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, """
                    {"content":[
                        {"type":"text","text":"done"},
                        {"type":"tool_use","id":"t2","name":"submit","input":{"x":2}}
                     ],
                     "stop_reason":"tool_use",
                     "usage":{"input_tokens":3,"output_tokens":4}}
                    """);
        });
        server.start();
        try {
            AnthropicMessagesModelClient client = new AnthropicMessagesModelClient();
            ModelRuntimeConfig config = ModelRuntimeConfig.builder()
                    .provider("ANTHROPIC_MESSAGES")
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                    .apiKey("test-key")
                    .modelId("claude-test")
                    .connectTimeoutMs(2000)
                    .readTimeoutMs(2000)
                    .build();

            ModelTool tool = new ModelTool("function",
                    new ModelTool.FunctionDef("submit", "submit result",
                            Map.of("type", "object", "properties", Map.of("x", Map.of("type", "integer")))));
            ToolCall call = new ToolCall("t1", "function",
                    new ToolCall.Function("submit", "{\"x\":1}"));
            ModelRequest request = new ModelRequest();
            request.setMessages(List.of(
                    ModelMessage.system("sys"),
                    ModelMessage.user("do it"),
                    ModelMessage.assistant(null, List.of(call)),
                    ModelMessage.tool("t1", "{\"ok\":true}")
            ));
            request.setTools(List.of(tool));
            request.setToolChoice(Map.of("type", "function", "function", Map.of("name", "submit")));
            request.setMaxTokens(64);

            ModelResponse response = client.chat(request, config);

            String sent = body.get();
            assertTrue(sent.contains("\"system\":\"sys\""));
            assertTrue(sent.contains("\"type\":\"tool_use\""));
            assertTrue(sent.contains("\"type\":\"tool_result\""));
            assertTrue(sent.contains("\"input_schema\""));
            assertTrue(sent.contains("\"type\":\"tool\""));
            assertTrue(sent.contains("\"name\":\"submit\""));

            assertEquals("done", response.getChoices().get(0).getMessage().getContent());
            assertEquals("submit", response.getChoices().get(0).getMessage().getToolCalls().get(0)
                    .getFunction().getName());
            assertEquals(7, response.getUsage().getTotalTokens());
            assertEquals("tool_calls", response.getChoices().get(0).getFinishReason());
        } finally {
            server.stop(0);
        }
    }

    private void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
