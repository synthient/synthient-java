package com.synthient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.Headers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ResponseHookTest {
    @Test
    void seesRetriesPagesAndUndecodableResponses() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        List<ResponseMetadata> seen = new CopyOnWriteArrayList<>();
        try (TestServer server = new TestServer(exchange -> {
                    int call = calls.getAndIncrement();
                    exchange.getResponseHeaders().set("X-Request-Id", "req-" + call);
                    exchange.getResponseHeaders().set("RateLimit-Limit", "60");
                    exchange.getResponseHeaders().set("RateLimit-Remaining", String.valueOf(59 - call));
                    exchange.getResponseHeaders().set("RateLimit-Reset", "2");
                    switch (call) {
                        case 0 -> {
                            exchange.getResponseHeaders().set("Retry-After", "1");
                            TestServer.json(exchange, 429, "{}");
                        }
                        case 1 ->
                            TestServer.json(
                                    exchange, 200, "{\"stream\":\"proxies\",\"feeds\":[],\"next_cursor\":\"c1\"}");
                        default -> TestServer.json(exchange, 200, "{\"stream\":");
                    }
                });
                SynthientClient client =
                        server.clientBuilder().responseHook(seen::add).build()) {
            assertThrows(
                    SynthientException.class,
                    () -> client.proxies().allExports().forEach(export -> {}));
            assertEquals(
                    List.of(429, 200, 200),
                    seen.stream().map(ResponseMetadata::statusCode).toList());
            assertEquals(
                    List.of("req-0", "req-1", "req-2"),
                    seen.stream().map(ResponseMetadata::requestId).toList());
            assertEquals(
                    new RateLimit(60, 58, Duration.ofSeconds(2)), seen.get(1).rateLimit());
        }
    }

    @Test
    void aThrowingHookFailsTheCall() throws Exception {
        RuntimeException failure = new IllegalStateException("hook failed");
        try (TestServer server = new TestServer(exchange -> TestServer.json(exchange, 200, "{\"ip\":\"1.1.1.1\"}"));
                SynthientClient client = server.clientBuilder()
                        .responseHook(metadata -> {
                            throw failure;
                        })
                        .build()) {
            assertSame(failure, assertThrows(IllegalStateException.class, () -> client.lookupIp("1.1.1.1")));
            assertEquals(1, server.requests.size());
        }
    }

    @Test
    void concurrentCallsSeeTheirOwnMetadata() throws Exception {
        Map<Thread, String> seen = new ConcurrentHashMap<>();
        try (TestServer server = new TestServer(exchange -> {
                    String ip = exchange.getRequestURI().getPath().replaceAll(".*/", "");
                    exchange.getResponseHeaders().set("X-Request-Id", ip);
                    TestServer.json(exchange, 200, "{\"ip\":\"" + ip + "\"}");
                });
                SynthientClient client = server.clientBuilder()
                        .responseHook(metadata -> seen.put(Thread.currentThread(), metadata.requestId()))
                        .build();
                ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                String ip = "10.0.0." + i;
                results.add(executor.submit(
                        () -> client.lookupIp(ip).ip().equals(ip) && ip.equals(seen.get(Thread.currentThread()))));
            }
            for (Future<Boolean> result : results) {
                assertEquals(true, result.get());
            }
        }
    }

    @ParameterizedTest
    @CsvSource(
            nullValues = "null",
            value = {
                "100,  99,   1,           1",
                "100,  99,   -5,          0",
                "100,  99,   99999999999, 2147483648",
                "100,  null, 1,           null",
                "100,  x,    1,           null",
                "null, null, null,        null"
            })
    void parsesRateLimits(String limit, String remaining, String reset, Long expectedReset) {
        Map<String, String> headers = new HashMap<>();
        if (limit != null) {
            headers.put("RateLimit-Limit", limit);
        }
        if (remaining != null) {
            headers.put("RateLimit-Remaining", remaining);
        }
        if (reset != null) {
            headers.put("RateLimit-Reset", reset);
        }
        RateLimit parsed = RateLimit.fromHeaders(Headers.of(headers));
        if (expectedReset == null) {
            assertNull(parsed);
        } else {
            assertEquals(new RateLimit(100, 99, Duration.ofSeconds(expectedReset)), parsed);
        }
    }

    @Test
    void toStringLeavesOutTheHeaders() {
        Headers headers = Headers.of("Location", "https://storage.test/x?X-Amz-Signature=secret");
        ResponseMetadata metadata = new ResponseMetadata(307, "req-1", null, headers);
        assertFalse(metadata.toString().contains("secret"));
    }
}
