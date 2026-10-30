package com.asuka.backend.core.agent.ai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiChatModelClientTest {

    @Test
    void sendsConfiguredModelAndThinkingFlag() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, """
                    {"choices":[{"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}],
                     "usage":{"prompt_tokens":1,"completion_tokens":2,"total_tokens":3}}
                    """);
        });
        server.start();
        try {
            OpenAiChatModelClient client = new OpenAiChatModelClient();
            ModelRuntimeConfig config = runtime(server, true);
            ModelRequest request = new ModelRequest();
            request.setMessages(List.of(ModelMessage.user("hello")));
            ModelResponse response = client.chat(request, config);

            assertEquals("ok", response.getChoices().get(0).getMessage().getContent());
            assertTrue(body.get().contains("\"model\":\"test-model\""));
            assertTrue(body.get().contains("\"thinking\":{\"type\":\"disabled\"}"));

            ModelRuntimeConfig noThinking = runtime(server, false);
            client.chat(request, noThinking);
            assertFalse(body.get().contains("\"thinking\""));
        } finally {
            server.stop(0);
        }
    }

    private ModelRuntimeConfig runtime(HttpServer server, boolean sendThinking) {
        return ModelRuntimeConfig.builder()
                .provider("OPENAI_CHAT")
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .apiKey("test-key")
                .modelId("test-model")
                .maxOutputTokens(128)
                .connectTimeoutMs(2000)
                .readTimeoutMs(2000)
                .sendThinking(sendThinking)
                .build();
    }

    private void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
