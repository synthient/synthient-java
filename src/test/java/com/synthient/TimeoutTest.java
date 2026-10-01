package com.synthient;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.synthient.model.ProxyEvent;
import java.io.OutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TimeoutTest {
    private static final Duration TIMEOUT = Duration.ofMillis(300);

    @Test
    void stalledHeadersTimeOut() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try (TestServer server = new TestServer(exchange -> await(release));
                SynthientClient client = server.clientBuilder().timeout(TIMEOUT).build()) {
            assertThrows(ApiTimeoutException.class, () -> client.lookupIp("1.1.1.1"));
            assertEquals(1, server.requests.size());
            release.countDown();
        }
    }

    @Test
    void freeCallsRetryTimeouts() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try (TestServer server = new TestServer(exchange -> await(release));
                SynthientClient client =
                        server.clientBuilder().timeout(TIMEOUT).maxRetries(1).build()) {
            assertThrows(ApiTimeoutException.class, client::account);
            assertEquals(2, server.requests.size());
            release.countDown();
        }
    }

    @Test
    void stalledBodyTimesOut() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try (TestServer server = new TestServer(exchange -> {
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, 0);
                    OutputStream out = exchange.getResponseBody();
                    out.write("{\"ip\":".getBytes(UTF_8));
                    out.flush();
                    await(release);
                });
                SynthientClient client = server.clientBuilder().timeout(TIMEOUT).build()) {
            assertThrows(ApiTimeoutException.class, () -> client.lookupIp("1.1.1.1"));
            release.countDown();
        }
    }

    @Test
    void aSlowReaderDoesNotTimeOut() throws Exception {
        String body = "{\"ip\":\"10.0.0.0\"}\n{\"ip\":\"10.0.0.1\"}\n{\"ip\":\"10.0.0.2\"}\n";
        try (TestServer server =
                        new TestServer(exchange -> TestServer.respond(exchange, 200, "application/x-ndjson", body));
                SynthientClient client = server.clientBuilder().timeout(TIMEOUT).build();
                EventStream<ProxyEvent> events = client.proxies().stream()) {
            List<String> ips = new ArrayList<>();
            for (ProxyEvent event : events) {
                if (ips.isEmpty()) {
                    Thread.sleep(TIMEOUT.multipliedBy(2));
                }
                ips.add(event.ip());
            }
            assertEquals(List.of("10.0.0.0", "10.0.0.1", "10.0.0.2"), ips);
        }
    }

    @Test
    void theClientStillWorksAfterALateResponse() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try (TestServer server = new TestServer(exchange -> {
                    if (exchange.getRequestURI().getPath().endsWith("/1.1.1.1")) {
                        await(release);
                    }
                    TestServer.json(exchange, 200, "{\"ip\":\"2.2.2.2\"}");
                });
                SynthientClient client = server.clientBuilder().timeout(TIMEOUT).build()) {
            assertThrows(ApiTimeoutException.class, () -> client.lookupIp("1.1.1.1"));
            release.countDown();
            assertEquals("2.2.2.2", client.lookupIp("2.2.2.2").ip());
        }
    }

    @Test
    void interruptingABlockedCallStopsIt() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try (TestServer server = new TestServer(exchange -> await(release));
                SynthientClient client = server.client()) {
            Throwable error = server.interruptAfter(1, client::account);
            assertInstanceOf(ApiConnectionException.class, error);
            assertEquals(1, server.requests.size());
            release.countDown();
        }
    }

    @Test
    void interruptingPaginationStopsIt() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try (TestServer server = new TestServer(exchange -> {
                    if (exchange.getRequestURI().getRawQuery() != null) {
                        await(release);
                    }
                    TestServer.json(exchange, 200, "{\"stream\":\"proxies\",\"feeds\":[],\"next_cursor\":\"c1\"}");
                });
                SynthientClient client = server.client()) {
            Throwable error =
                    server.interruptAfter(2, () -> client.proxies().allExports().forEach(export -> {}));
            assertInstanceOf(ApiConnectionException.class, error);
            assertEquals(2, server.requests.size());
            release.countDown();
        }
    }

    @Test
    void closingAStreamFromAnotherThreadEndsIteration() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try (TestServer server = new TestServer(exchange -> {
                    exchange.sendResponseHeaders(200, 0);
                    OutputStream out = exchange.getResponseBody();
                    out.write("{\"ip\":\"10.0.0.1\"}\n".getBytes(UTF_8));
                    out.flush();
                    await(release);
                });
                SynthientClient client = server.client()) {
            EventStream<ProxyEvent> events = client.proxies().stream();
            Iterator<ProxyEvent> iterator = events.iterator();
            assertEquals("10.0.0.1", iterator.next().ip());
            CompletableFuture<Boolean> hasNext = CompletableFuture.supplyAsync(iterator::hasNext);
            Thread.sleep(100);
            events.close();
            assertFalse(hasNext.get(5, TimeUnit.SECONDS));
            release.countDown();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 503, 307})
    void retriesAndRedirectsDoNotWaitForUnusedBodies(int status) throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        try (TestServer server = new TestServer(exchange -> {
                    if (calls.getAndIncrement() == 0) {
                        exchange.getResponseHeaders().set("Retry-After", "0");
                        exchange.getResponseHeaders().set("Location", "/storage/export.parquet");
                        exchange.sendResponseHeaders(status, 100);
                        exchange.getResponseBody().flush();
                        await(release);
                    } else {
                        TestServer.json(exchange, 200, "{\"stream\":\"proxies\",\"feeds\":[]}");
                    }
                });
                SynthientClient client = server.client()) {
            try {
                CompletableFuture<Void> result = CompletableFuture.runAsync(() -> {
                    if (status == 307) {
                        client.proxies().downloadUrl(ExportId.LATEST);
                    } else {
                        client.proxies().listExports();
                    }
                });
                result.get(2, TimeUnit.SECONDS);
                assertEquals(status == 307 ? 1 : 2, server.requests.size());
            } finally {
                release.countDown();
            }
        }
    }

    private static void await(CountDownLatch latch) {
        assertDoesNotThrow(() -> latch.await(30, TimeUnit.SECONDS));
    }
}
