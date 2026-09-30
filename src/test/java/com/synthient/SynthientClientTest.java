package com.synthient;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SynthientClientTest {
    @Test
    void readsTheKeyFromTheEnvironment() throws Exception {
        try (TestServer server = new TestServer(exchange -> TestServer.json(exchange, 200, "{\"status\":\"ok\"}"));
                SynthientClient client = SynthientClient.builder()
                        .baseUrl(server.baseUrl())
                        .environment(Map.of("SYNTHIENT_API_KEY", " env-key "))
                        .build()) {
            client.health();
            assertEquals("env-key", server.requests.getFirst().header("X-Api-Key"));
        }
    }

    @Test
    void requiresAKey() {
        SynthientClient.Builder missing = SynthientClient.builder().environment(Map.of());
        assertThrows(IllegalArgumentException.class, missing::build);
        SynthientClient.Builder blank = SynthientClient.builder().environment(Map.of("SYNTHIENT_API_KEY", "  "));
        assertThrows(IllegalArgumentException.class, blank::build);
        assertThrows(IllegalArgumentException.class, () -> new SynthientClient(" \t"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "ftp://api.synthient.com",
                "https://",
                "api.synthient.com",
                "https://api.synthient.com/api/v4?x=1",
                "https://api.synthient.com/api/v4#top",
                "https://user:pass@api.synthient.com/api/v4",
                "http://localhost:99999"
            })
    void rejectsInvalidBaseUrls(String baseUrl) {
        assertThrows(
                IllegalArgumentException.class, () -> SynthientClient.builder().baseUrl(baseUrl));
    }

    @Test
    void rejectsInvalidOptions() {
        SynthientClient.Builder builder = SynthientClient.builder();
        assertThrows(IllegalArgumentException.class, () -> builder.maxRetries(-1));
        assertThrows(IllegalArgumentException.class, () -> builder.timeout(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> builder.timeout(Duration.ofSeconds(-1)));
    }

    @Test
    void sendsHeadersAndUsesTheBasePath() throws Exception {
        try (TestServer server = new TestServer(exchange -> TestServer.json(exchange, 200, TestServer.IP_INFO));
                SynthientClient client = SynthientClient.builder()
                        .apiKey("test-key")
                        .baseUrl(server.origin() + "/prefix/api/v4/")
                        .userAgent("my-app/1.2")
                        .build()) {
            client.lookupIp("8.8.8.8");
            TestServer.Request request = server.requests.getFirst();
            assertEquals("GET", request.method());
            assertEquals("/prefix/api/v4/lookup/ip/8.8.8.8", request.path());
            assertEquals("test-key", request.header("X-Api-Key"));
            assertEquals("synthient-java/" + SynthientClient.VERSION + " my-app/1.2", request.header("User-Agent"));
            assertEquals("application/json", request.header("Accept"));
            assertEquals("", request.header("Content-Type"));
        }
    }

    @Test
    void buildersCanBeReused() throws Exception {
        try (TestServer server = new TestServer(exchange -> TestServer.json(exchange, 200, "{}"))) {
            SynthientClient.Builder builder = server.clientBuilder();
            SynthientClient first = builder.build();
            SynthientClient second = builder.userAgent("second/1").build();
            first.close();
            assertThrows(IllegalStateException.class, first::health);
            assertDoesNotThrow(second::health);
            assertEquals(
                    "synthient-java/" + SynthientClient.VERSION + " second/1",
                    server.requests.getFirst().header("User-Agent"));
            assertNotSame(first, second);
            second.close();
        }
    }

    @Test
    void closingLeavesABorrowedClientUsable() throws Exception {
        try (TestServer server = new TestServer(exchange -> TestServer.json(exchange, 200, "{}"))) {
            OkHttpClient http = new OkHttpClient();
            SynthientClient borrowing = server.clientBuilder().httpClient(http).build();
            borrowing.health();
            borrowing.close();
            borrowing.close();
            assertThrows(IllegalStateException.class, borrowing::health);
            assertFalse(http.dispatcher().executorService().isShutdown());
            try (Response response = http.newCall(new Request.Builder()
                            .url(server.baseUrl() + "/health")
                            .build())
                    .execute()) {
                assertEquals(200, response.code());
            }
            http.dispatcher().executorService().shutdown();
        }
    }

    @Test
    void toStringHidesTheKey() {
        try (SynthientClient client = new SynthientClient("secret-key")) {
            assertFalse(client.toString().contains("secret-key"));
        }
    }

    @Test
    void versionMatchesTheBuild() {
        assertEquals(System.getProperty("synthient.version"), SynthientClient.VERSION);
    }

    @Test
    void emptyBatchSendsNothing() throws Exception {
        try (TestServer server = new TestServer(exchange -> TestServer.json(exchange, 200, "{}"));
                SynthientClient client = server.client()) {
            assertEquals(List.of(), client.lookupIps(List.of()));
            assertTrue(server.requests.isEmpty());
        }
    }
}
