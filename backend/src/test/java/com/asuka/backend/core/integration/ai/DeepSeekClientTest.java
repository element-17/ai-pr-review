package com.asuka.backend.core.integration.ai;

import com.asuka.backend.common.properties.AiProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepSeekClientTest {

    @Test
    void sendsOpenAiCompatibleRequestAndParsesToolCall() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String response = """
                    {
                      "choices": [{
                        "message": {
                          "role": "assistant",
                          "content": null,
                          "tool_calls": [{
                            "id": "call-1",
                            "type": "function",
                            "function": {
                              "name": "submit_summary",
                              "arguments": "{\\"purpose\\":\\"p\\"}"
                            }
                          }]
                        },
                        "finish_reason": "tool_calls"
                      }],
                      "usage": {"prompt_tokens": 3, "completion_tokens": 2, "total_tokens": 5}
                    }
                    """;
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            AiProperties properties = new AiProperties();
            properties.getDeepseek().setApiKey("test-key");
            properties.getDeepseek().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            DeepSeekClient client = new DeepSeekClient(properties);

            ModelRequest request = new ModelRequest();
            request.setMessages(List.of(ModelMessage.user("hello")));
            request.setTools(List.of());

            ModelResponse response = client.chat(request);

            assertEquals("submit_summary",
                    response.getChoices().get(0).getMessage().getToolCalls().get(0)
                            .getFunction().getName());
            assertTrue(requestBody.get().contains("\"model\":\"deepseek-flash\""));
            assertTrue(requestBody.get().contains("\"tool_choice\":\"auto\""));
            assertTrue(requestBody.get().contains("\"thinking\":{\"type\":\"disabled\"}"));
        } finally {
            server.stop(0);
        }
    }
}
