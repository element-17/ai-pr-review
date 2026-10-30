package com.asuka.backend.core.github;

import com.asuka.backend.common.exception.GitHubApiException;
import com.asuka.backend.common.properties.GitHubProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GitHubClientAuthTest {

    @Test
    void sendsTokenFromExplicitAuth() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/repos/owner/repo/pulls/1", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, """
                    {"number":1,"title":"test","state":"open","head":{"sha":"abc"},"user":{"login":"u"}}
                    """);
        });
        server.createContext("/repos/owner/repo/pulls/2", exchange -> respond(exchange, 401, "{}"));
        server.start();
        try {
            GitHubClient client = new GitHubClient(properties(server));

            client.getPullRequest("owner", "repo", 1, GitHubAuth.user("user-token", 1L));
            assertEquals("Bearer user-token", authorization.get());

            client.getPullRequest("owner", "repo", 1, GitHubAuth.shared("shared-token"));
            assertEquals("Bearer shared-token", authorization.get());

            client.getPullRequest("owner", "repo", 1, GitHubAuth.none());
            assertNull(authorization.get());

            GitHubApiException unauthorized = assertThrows(GitHubApiException.class,
                    () -> client.getPullRequest("owner", "repo", 2, GitHubAuth.user("bad-token", 1L)));
            assertEquals(GitHubApiException.Kind.UNAUTHORIZED, unauthorized.getKind());
        } finally {
            server.stop(0);
        }
    }

    private GitHubProperties properties(HttpServer server) {
        GitHubProperties properties = new GitHubProperties();
        properties.setApiBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setToken("");
        return properties;
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
