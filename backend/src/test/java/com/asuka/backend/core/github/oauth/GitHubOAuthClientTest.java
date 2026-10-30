package com.asuka.backend.core.github.oauth;

import com.asuka.backend.common.properties.GitHubOAuthProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubOAuthClientTest {

    @Test
    void callsGitHubOAuthEndpoints() throws Exception {
        AtomicReference<String> tokenRequestBody = new AtomicReference<>();
        AtomicReference<String> userAuthorization = new AtomicReference<>();
        AtomicReference<String> revokeAuthorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/login/oauth/access_token", exchange -> {
            tokenRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, """
                    {"access_token":"gho_test_token","token_type":"bearer","scope":"read:user,user:email"}
                    """);
        });
        server.createContext("/user", exchange -> {
            userAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, """
                    {"id":12345,"login":"octocat","name":"Octo Cat","avatar_url":"https://avatars.example/u","email":null}
                    """);
        });
        server.createContext("/user/emails", exchange -> respond(exchange, 200, """
                [{"email":"octo@example.com","primary":true,"verified":true}]
                """));
        server.createContext("/applications/client-id/token", exchange -> {
            revokeAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();

        try {
            GitHubOAuthProperties properties = new GitHubOAuthProperties();
            properties.setClientId("client-id");
            properties.setClientSecret("client-secret");
            properties.setCallbackUrl("http://localhost:8080/api/auth/github/callback");
            properties.setAuthorizeUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/login/oauth/authorize");
            properties.setTokenUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/login/oauth/access_token");
            properties.setUserApiUrl("http://127.0.0.1:" + server.getAddress().getPort());
            GitHubOAuthClient client = new GitHubOAuthClient(properties);

            String authorizeUrl = client.buildAuthorizeUrl("state-value");
            assertTrue(authorizeUrl.contains("client_id=client-id"));
            assertTrue(authorizeUrl.contains("state=state-value"));
            assertTrue(authorizeUrl.contains("scope="));
            assertTrue(authorizeUrl.contains("read"));

            GitHubOAuthTokenResponse token = client.exchangeCode("code-value");
            assertEquals("gho_test_token", token.getAccessToken());
            assertTrue(tokenRequestBody.get().contains("client_secret=client-secret"));
            assertTrue(tokenRequestBody.get().contains("code=code-value"));

            GitHubOAuthUser user = client.getUser(token.getAccessToken());
            assertEquals(12345L, user.getId());
            assertEquals("octocat", user.getLogin());
            assertEquals("Bearer gho_test_token", userAuthorization.get());

            assertEquals("octo@example.com", client.getPrimaryEmail(token.getAccessToken()));

            client.revokeToken(token.getAccessToken());
            assertTrue(revokeAuthorization.get() != null && revokeAuthorization.get().startsWith("Basic "));
        } finally {
            server.stop(0);
        }
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
