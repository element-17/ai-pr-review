package com.asuka.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebMvcConfigTest {
    @Test
    void allowsConfiguredHttpsOriginForPreflightAndPostButRejectsOtherOrigins() throws Exception {
        var filter = new WebMvcConfig().corsFilter("https://example.com/ai-pr-review").getFilter();
        for (String method : new String[]{"OPTIONS", "POST"}) {
            var request = new MockHttpServletRequest(method, "/api/auth/login");
            request.addHeader("Origin", "https://example.com");
            if (method.equals("OPTIONS")) {
                request.addHeader("Access-Control-Request-Method", "POST");
                request.addHeader("Access-Control-Request-Headers", "content-type,authorization");
            }
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertEquals(200, response.getStatus());
            assertEquals("https://example.com", response.getHeader("Access-Control-Allow-Origin"));
        }
        var request = new MockHttpServletRequest("OPTIONS", "/api/auth/login");
        request.addHeader("Origin", "https://untrusted.example");
        request.addHeader("Access-Control-Request-Method", "POST");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertEquals(403, response.getStatus());
    }

    @Test
    void rejectsInvalidFrontendUrl() {
        assertThrows(IllegalArgumentException.class, () -> new WebMvcConfig().corsFilter("https://user@example.com/path"));
    }
}
