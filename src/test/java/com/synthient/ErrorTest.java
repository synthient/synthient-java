package com.synthient;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ErrorTest {
    private static ApiException failWith(TestServer.Handler handler) throws Exception {
        try (TestServer server = new TestServer(handler);
                SynthientClient client = server.client()) {
            return assertThrows(ApiException.class, () -> client.lookupIp("1.1.1.1"));
        }
    }

    @Test
    void parsesProblemDetails() throws Exception {
        ApiException error = failWith(exchange -> {
            exchange.getResponseHeaders().set("X-Request-Id", "req-header");
            TestServer.respond(
                    exchange,
                    403,
                    "application/problem+json",
                    "{\"type\":\"https://docs.synthient.com/errors/forbidden\",\"title\":\"Forbidden\","
                            + "\"status\":403,\"detail\":\"needs PROXY_FEEDS\",\"instance\":\"req-body\"}");
        });
        assertEquals(403, error.statusCode());
        assertEquals("https://docs.synthient.com/errors/forbidden", error.type());
        assertEquals("Forbidden", error.title());
        assertEquals("needs PROXY_FEEDS", error.detail());
        assertEquals("req-header", error.requestId());
        assertEquals("403 Forbidden", error.getMessage());
        assertNull(error.retryAfter());
    }

    @Test
    void fallsBackToTheInstanceForTheRequestId() throws Exception {
        ApiException error = failWith(exchange -> TestServer.respond(
                exchange, 404, "application/problem+json", "{\"title\":\"Not Found\",\"instance\":\"req-body\"}"));
        assertEquals("req-body", error.requestId());
    }

    @Test
    void parsesValidationErrors() throws Exception {
        ApiException error = failWith(exchange -> TestServer.respond(
                exchange,
                422,
                "application/problem+json",
                "{\"title\":\"Validation failed\",\"errors\":{\"ip_address\":[\"Invalid IP address format.\"]}}"));
        assertEquals(Map.of("ip_address", List.of("Invalid IP address format.")), error.errors());
        assertEquals("", error.detail());
        assertEquals("422 Unprocessable Content", error.getMessage());
    }

    @Test
    void ignoresErrorsOfTheWrongShape() throws Exception {
        ApiException error = failWith(exchange -> TestServer.respond(
                exchange, 422, "application/json", "{\"title\":\"x\",\"errors\":{\"ip\":\"not a list\"}}"));
        assertEquals(Map.of(), error.errors());
        assertEquals("x", error.title());
    }

    @Test
    void readsPlainTextErrors() throws Exception {
        ApiException unauthorized =
                failWith(exchange -> TestServer.respond(exchange, 401, "text/plain", "401 invalid api key\n"));
        assertEquals("401 invalid api key", unauthorized.detail());
        ApiException notFound = failWith(
                exchange -> TestServer.respond(exchange, 404, "text/plain; charset=utf-8", "404 page not found"));
        assertEquals("404 page not found", notFound.detail());
    }

    @Test
    void keepsTheStatusOfEmptyAndHtmlBodies() throws Exception {
        ApiException empty = failWith(exchange -> {
            exchange.getResponseHeaders().set("Retry-After", "7");
            TestServer.respond(exchange, 404, "", "");
        });
        assertEquals(404, empty.statusCode());
        assertEquals(Duration.ofSeconds(7), empty.retryAfter());
        ApiException html = failWith(exchange ->
                TestServer.respond(exchange, 502, "text/html", "<html>" + "x".repeat(128 << 10) + "</html>"));
        assertEquals(502, html.statusCode());
        assertEquals("", html.detail());
        assertEquals("502 Bad Gateway", html.getMessage());
    }

    @Test
    void messagesLeaveOutServerDataAndTheKey() throws Exception {
        ApiException error = failWith(exchange -> TestServer.respond(
                exchange, 418, "application/json", "{\"title\":\"secret title\",\"detail\":\"secret detail\"}"));
        assertEquals("418", error.getMessage());
        assertFalse(error.toString().contains("secret"));
        assertFalse(error.toString().contains(TestServer.API_KEY));
    }

    @Test
    void rejectsOversizedSuccessBodies() throws Exception {
        try (TestServer server = new TestServer(exchange -> {
                    exchange.sendResponseHeaders(200, 0);
                    byte[] chunk = new byte[1 << 20];
                    Arrays.fill(chunk, (byte) ' ');
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write("{\"ip\":\"1.1.1.1\"}".getBytes(UTF_8));
                        for (int i = 0; i < 17; i++) {
                            out.write(chunk);
                        }
                    }
                });
                SynthientClient client = server.client()) {
            SynthientException error = assertThrows(SynthientException.class, () -> client.lookupIp("1.1.1.1"));
            assertTrue(error.getMessage().contains("exceeds"));
        }
    }
}
