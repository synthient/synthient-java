package com.synthient;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RetryTest {
    private static final String PAGE = "{\"stream\":\"proxies\",\"feeds\":[]}";
    private static final String LOOKUP = "{\"ip\":\"1.1.1.1\"}";

    /**
     * Answers with the given statuses in turn and then 200 with the body. 429 carries {@code Retry-After: 1}
     * and 4290 is a 429 without it.
     */
    private static TestServer sequence(String body, int... statuses) throws IOException {
        AtomicInteger calls = new AtomicInteger();
        return new TestServer(exchange -> {
            int index = calls.getAndIncrement();
            int status = index < statuses.length ? statuses[index] : 200;
            if (status == 429) {
                exchange.getResponseHeaders().set("Retry-After", "1");
            }
            if (status == 4290) {
                status = 429;
            }
            TestServer.json(exchange, status, status == 200 ? body : "{}");
        });
    }

    @ParameterizedTest(name = "{0} call after {1}")
    @CsvSource({
        "free,   503 502,     3, true",
        "free,   503 503 503, 3, false",
        "free,   500 504,     3, true",
        "free,   400,         1, false",
        "free,   4290,        1, false",
        "free,   429,         2, true",
        "billed, 429 429,     3, true",
        "billed, 429 429 429, 3, false",
        "billed, 500,         1, false",
        "billed, 503,         1, false",
        "billed, 4290,        1, false",
    })
    void retryPolicy(String call, String statuses, int requests, boolean succeeds) throws Exception {
        int[] sequence = Arrays.stream(statuses.split(" ", -1))
                .mapToInt(Integer::parseInt)
                .toArray();
        boolean free = call.equals("free");
        try (TestServer server = sequence(free ? PAGE : LOOKUP, sequence);
                SynthientClient client = server.client()) {
            Runnable run = free ? client.proxies()::listExports : () -> client.lookupIp("1.1.1.1");
            if (succeeds) {
                assertDoesNotThrow(run::run);
            } else {
                assertThrows(ApiException.class, run::run);
            }
            assertEquals(requests, server.requests.size());
        }
    }

    @Test
    void waitsForRetryAfterAndBacksOff() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (TestServer server = new TestServer(exchange -> {
                    switch (calls.getAndIncrement()) {
                        case 0 -> {
                            exchange.getResponseHeaders().set("Retry-After", "3");
                            TestServer.json(exchange, 429, "{}");
                        }
                        case 1 -> TestServer.json(exchange, 503, "{}");
                        default -> TestServer.json(exchange, 200, PAGE);
                    }
                });
                SynthientClient client = server.client()) {
            client.proxies().listExports();
            assertEquals(2, server.sleeps.size());
            assertEquals(Duration.ofSeconds(3), server.sleeps.get(0));
            Duration backoff = server.sleeps.get(1);
            assertTrue(
                    backoff.compareTo(Duration.ofMillis(1500)) >= 0 && backoff.compareTo(Duration.ofMillis(2500)) < 0);
        }
    }

    @Test
    void longRetryAfterIsNotRetried() throws Exception {
        try (TestServer server = new TestServer(exchange -> {
                    exchange.getResponseHeaders().set("Retry-After", "120");
                    TestServer.json(exchange, 429, "{}");
                });
                SynthientClient client = server.client()) {
            ApiException error = assertThrows(ApiException.class, () -> client.lookupIp("1.1.1.1"));
            assertEquals(Duration.ofMinutes(2), error.retryAfter());
            assertEquals(1, server.requests.size());
            assertTrue(server.sleeps.isEmpty());
        }
    }

    @Test
    void zeroRetriesSendsOnce() throws Exception {
        try (TestServer server = sequence(PAGE, 503, 503);
                SynthientClient client = server.clientBuilder().maxRetries(0).build()) {
            assertThrows(ApiException.class, client.proxies()::listExports);
            assertEquals(1, server.requests.size());
        }
    }

    @Test
    void connectionFailuresAreRetriedOnlyForFreeCalls() throws Exception {
        List<Duration> sleeps = new CopyOnWriteArrayList<>();
        try (SynthientClient client = SynthientClient.builder()
                .apiKey(TestServer.API_KEY)
                .baseUrl("http://127.0.0.1:" + TestServer.closedPort() + "/api/v4")
                .sleeper(sleeps::add)
                .build()) {
            assertThrows(ApiConnectionException.class, client::account);
            assertEquals(2, sleeps.size());
            sleeps.clear();
            assertThrows(ApiConnectionException.class, () -> client.lookupIp("1.1.1.1"));
            assertTrue(sleeps.isEmpty());
        }
    }

    @Test
    void billedPostIsNotReplayedOnAStaleConnection() throws Exception {
        try (TestServer server = new TestServer(exchange -> {
                    if (exchange.getRequestURI().getPath().endsWith("/health")) {
                        TestServer.json(exchange, 200, "{}");
                    }
                });
                SynthientClient client = server.client()) {
            client.health();
            assertThrows(ApiConnectionException.class, () -> client.lookupIps(List.of("1.1.1.1")));
            assertEquals(List.of("/api/v4/health", "/api/v4/lookup/ips"), server.paths());
        }
    }

    @Test
    void billedGetIsNotResentOnAStaleConnection() throws Exception {
        try (TestServer server = new TestServer(exchange -> {
                    if (exchange.getRequestURI().getPath().endsWith("/health")) {
                        TestServer.json(exchange, 200, "{}");
                    }
                });
                SynthientClient client = server.client()) {
            client.health();
            assertThrows(ApiConnectionException.class, () -> client.lookupIp("1.1.1.1"));
            assertThrows(ApiConnectionException.class, () -> client.lookupDomain("example.com"));
            assertEquals(
                    List.of("/api/v4/health", "/api/v4/lookup/ip/1.1.1.1", "/api/v4/lookup/domain/example.com"),
                    server.paths());
            assertTrue(server.sleeps.isEmpty());
        }
    }

    @Test
    void billedGetIsNotResentWhenAFreshConnectionFails() throws Exception {
        try (TestServer server = new TestServer(exchange -> {});
                SynthientClient client = server.client()) {
            assertThrows(ApiConnectionException.class, () -> client.lookupIp("1.1.1.1"));
            assertEquals(List.of("/api/v4/lookup/ip/1.1.1.1"), server.paths());
        }
    }

    /** OkHttp would resend after a 503 with {@code Retry-After: 0} and fail on an all-digit value too large for an int. */
    @ParameterizedTest
    @CsvSource({"0, 0", "99999999999, 2147483648"})
    void a503RetryAfterIsOnlyReadByTheSdk(String retryAfter, long seconds) throws Exception {
        try (TestServer server = new TestServer(exchange -> {
                    exchange.getResponseHeaders().set("Retry-After", retryAfter);
                    TestServer.json(exchange, 503, "{}");
                });
                SynthientClient client = server.client()) {
            ApiException error = assertThrows(ApiException.class, () -> client.lookupIp("1.1.1.1"));
            assertEquals(503, error.statusCode());
            assertEquals(Duration.ofSeconds(seconds), error.retryAfter());
            assertEquals(1, server.requests.size());
        }
    }

    @Test
    void interruptionDuringBackoffStops() throws Exception {
        try (TestServer server = new TestServer(exchange -> {
                    exchange.getResponseHeaders().set("Retry-After", "30");
                    TestServer.json(exchange, 503, "{}");
                });
                SynthientClient client =
                        server.clientBuilder().sleeper(Thread::sleep).build()) {
            Throwable error = server.interruptAfter(1, client::account);
            assertInstanceOf(ApiConnectionException.class, error);
            assertInstanceOf(InterruptedException.class, error.getCause());
            assertEquals(1, server.requests.size());
        }
    }

    @ParameterizedTest
    @CsvSource(
            nullValues = "null",
            value = {
                "5, 5",
                "' 7 ', 7",
                "0, 0",
                "-3, 0",
                "99999999999, 2147483648",
                "999999999999999999999999, 2147483648",
                "abc, null",
                "'', null",
                "1.5, null",
                "'Wed, 21 Oct 2015 07:28:00 GMT', 0"
            })
    void parsesRetryAfter(String value, Long seconds) {
        Duration parsed = Transport.parseRetryAfter(value);
        if (seconds == null) {
            assertNull(parsed);
        } else {
            assertEquals(Duration.ofSeconds(seconds), parsed);
        }
    }

    @Test
    void parsesRetryAfterDates() {
        String date = DateTimeFormatter.RFC_1123_DATE_TIME.format(
                ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(30));
        Duration parsed = Transport.parseRetryAfter(date);
        assertTrue(parsed != null
                && parsed.compareTo(Duration.ofSeconds(28)) > 0
                && parsed.compareTo(Duration.ofSeconds(31)) < 0);
    }

    @Test
    void backoffGrowsWithJitterUpToThirtySeconds() {
        for (int attempt = 0; attempt < 8; attempt++) {
            double base = Math.min(1L << attempt, 30);
            for (int i = 0; i < 100; i++) {
                double seconds = Transport.backoff(attempt).toNanos() / 1e9;
                assertTrue(seconds >= base * 0.75 && seconds < base * 1.25, attempt + ": " + seconds);
            }
        }
    }
}
