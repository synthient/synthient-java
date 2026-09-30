package com.synthient;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** A loopback HTTP server that records every request and answers with a handler. */
final class TestServer implements AutoCloseable {
    static final String API_KEY = "test-key";

    record Request(String method, String path, String query, Headers headers, String body) {
        String header(String name) {
            String value = headers.getFirst(name);
            return value == null ? "" : value;
        }
    }

    interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }

    final List<Request> requests = new CopyOnWriteArrayList<>();
    final List<Duration> sleeps = new CopyOnWriteArrayList<>();
    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    TestServer(Handler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), UTF_8);
            String query = exchange.getRequestURI().getRawQuery();
            requests.add(new Request(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getRawPath(),
                    query == null ? "" : query,
                    exchange.getRequestHeaders(),
                    body));
            try {
                handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    String origin() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    String baseUrl() {
        return origin() + "/api/v4";
    }

    /** Returns a builder for a client of this server that records backoff delays instead of sleeping. */
    SynthientClient.Builder clientBuilder() {
        return SynthientClient.builder().apiKey(API_KEY).baseUrl(baseUrl()).sleeper(sleeps::add);
    }

    SynthientClient client() {
        return clientBuilder().build();
    }

    List<String> paths() {
        return requests.stream().map(Request::path).toList();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    /**
     * Runs a call on another thread, interrupts it once the server has received the given number of requests
     * and the thread is blocked, and returns what the call threw.
     */
    Throwable interruptAfter(int requestCount, Runnable call) throws Exception {
        CompletableFuture<Throwable> failure = new CompletableFuture<>();
        Thread thread = Thread.ofPlatform().start(() -> {
            try {
                call.run();
                failure.complete(new AssertionError("the call returned"));
            } catch (RuntimeException e) {
                failure.complete(Thread.currentThread().isInterrupted() ? e : new AssertionError("flag cleared", e));
            }
        });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (requests.size() < requestCount
                || !(thread.getState() == Thread.State.WAITING || thread.getState() == Thread.State.TIMED_WAITING)) {
            assertTrue(System.nanoTime() < deadline, "the call did not block");
            Thread.sleep(5);
        }
        thread.interrupt();
        return failure.get(5, TimeUnit.SECONDS);
    }

    /** Returns a local port that nothing listens on. */
    static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    static List<Feed<?>> feeds(SynthientClient client) {
        return List.of(
                client.proxies(),
                client.anonymizers(),
                client.torrents(),
                client.ja4t(),
                client.heliosHttp(),
                client.heliosHttps(),
                client.heliosDns(),
                client.heliosAdb());
    }

    static void json(HttpExchange exchange, int status, String body) throws IOException {
        respond(exchange, status, "application/json", body);
    }

    static void respond(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(UTF_8);
        if (!contentType.isEmpty()) {
            exchange.getResponseHeaders().set("Content-Type", contentType);
        }
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    static final String IP_INFO = """
            {
              "ip": "8.8.8.8",
              "network": {"asn": 15169, "isp": "Google LLC", "type": "DATACENTER", "org": null,
                          "domain": "google.com", "abuse_email": "network-abuse@google.com"},
              "location": {"country": "US", "state": "California", "city": "Mountain View",
                           "timezone": "America/Los_Angeles", "latitude": 37.4056, "longitude": -122.0775,
                           "geo_hash": "9q9hvu"},
              "intelligence": {
                "risk_score": 12,
                "categories": ["DATACENTER"],
                "behavior": [],
                "providers": [{"provider": "google", "type": "DATACENTER", "last_seen": 1780000000}],
                "devices": [{"os": "Linux", "version": "6.1", "last_seen": 1780000100}]
              },
              "future_field": {"ignored": true}
            }
            """;
}
