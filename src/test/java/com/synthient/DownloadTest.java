package com.synthient;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class DownloadTest {
    private static final String SIGNATURE = "secret-signature";
    private static final TestServer.Handler PARQUET =
            exchange -> TestServer.respond(exchange, 200, "application/octet-stream", "PAR1data");

    /** Serves the API under /api/v4, which redirects to the location, and export storage everywhere else. */
    private static TestServer server(String location, TestServer.Handler storage) throws Exception {
        return new TestServer(exchange -> {
            if (exchange.getRequestURI().getPath().startsWith("/api/v4/")) {
                exchange.getResponseHeaders().set("Location", location);
                TestServer.respond(exchange, 307, "", "");
            } else {
                storage.handle(exchange);
            }
        });
    }

    @Test
    void downloadsWithoutTheApiKey() throws Exception {
        try (TestServer server = server("/storage/proxies.parquet?X-Amz-Signature=" + SIGNATURE, PARQUET);
                SynthientClient client = server.client();
                InputStream file = client.proxies().download("2026-05-03/07")) {
            assertEquals("PAR1data", new String(file.readAllBytes(), UTF_8));
            TestServer.Request api = server.requests.get(0);
            TestServer.Request storage = server.requests.get(1);
            assertEquals("/api/v4/feeds/proxies/export/2026-05-03/7", api.path());
            assertEquals(TestServer.API_KEY, api.header("X-Api-Key"));
            assertEquals("/storage/proxies.parquet", storage.path());
            assertEquals("X-Amz-Signature=" + SIGNATURE, storage.query());
            assertEquals("", storage.header("X-Api-Key"));
            assertEquals("synthient-java/" + SynthientClient.VERSION, storage.header("User-Agent"));
        }
    }

    @Test
    void resolvesTheDownloadUrl() throws Exception {
        try (TestServer server = server("/storage/x.parquet?X-Amz-Signature=" + SIGNATURE, PARQUET);
                SynthientClient client = server.client()) {
            URI url = client.proxies().downloadUrl(ExportId.LATEST);
            assertEquals(server.origin() + "/storage/x.parquet?X-Amz-Signature=" + SIGNATURE, url.toString());
            assertEquals(1, server.requests.size());
        }
    }

    @Test
    void aBorrowedClientNeverFollowsApiRedirects() throws Exception {
        OkHttpClient following =
                new OkHttpClient.Builder().followRedirects(true).build();
        try (TestServer server = server("/storage/x.parquet?X-Amz-Signature=" + SIGNATURE, PARQUET);
                SynthientClient client =
                        server.clientBuilder().httpClient(following).build()) {
            URI url = client.proxies().downloadUrl(ExportId.LATEST);
            assertEquals("/storage/x.parquet", url.getPath());
            assertEquals(List.of("/api/v4/feeds/proxies/export/latest"), server.paths());
        } finally {
            following.dispatcher().executorService().shutdown();
        }
    }

    @Test
    void followsStorageRedirects() throws Exception {
        try (TestServer server = server("/storage/first", exchange -> {
                    if (exchange.getRequestURI().getPath().equals("/storage/first")) {
                        exchange.getResponseHeaders().set("Location", "/storage/second?X-Amz-Signature=" + SIGNATURE);
                        TestServer.respond(exchange, 302, "", "");
                    } else {
                        PARQUET.handle(exchange);
                    }
                });
                SynthientClient client = server.client();
                InputStream file = client.proxies().download(ExportId.LATEST)) {
            assertEquals("PAR1data", new String(file.readAllBytes(), UTF_8));
            assertEquals("/storage/second", server.requests.get(2).path());
            assertEquals("", server.requests.get(2).header("X-Api-Key"));
        }
    }

    @Test
    void stopsFollowingEndlessStorageRedirects() throws Exception {
        try (TestServer server = server("/storage/again", exchange -> {
                    exchange.getResponseHeaders().set("Location", "/storage/again?X-Amz-Signature=" + SIGNATURE);
                    TestServer.respond(exchange, 302, "", "");
                });
                SynthientClient client = server.client()) {
            ApiConnectionException error = assertThrows(
                    ApiConnectionException.class, () -> client.proxies().download(ExportId.LATEST));
            assertNoSignature(error);
        }
    }

    @ParameterizedTest
    @CsvSource({
        "https://api.test/x, https://storage.test/x, true",
        "http://api.test/x, http://storage.test/x, true",
        "http://api.test/x, https://storage.test/x, true",
        "https://api.test/x, http://storage.test/x, false",
        "https://api.test/x, https://user:pass@storage.test/x, false",
    })
    void checksRedirects(String from, String target, boolean allowed) {
        HttpUrl fromUrl = HttpUrl.get(from);
        HttpUrl targetUrl = HttpUrl.get(target);
        if (allowed) {
            assertDoesNotThrow(() -> Feed.checkRedirect(fromUrl, targetUrl));
        } else {
            SynthientException error =
                    assertThrows(SynthientException.class, () -> Feed.checkRedirect(fromUrl, targetUrl));
            assertFalse(error.getMessage().contains("storage.test"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ftp://storage.test/x", "file:///etc/passwd", "http://[bad " + SIGNATURE})
    void rejectsUnusableLocations(String location) throws Exception {
        try (TestServer server = server(location, PARQUET);
                SynthientClient client = server.client()) {
            SynthientException error = assertThrows(
                    SynthientException.class, () -> client.proxies().downloadUrl(ExportId.LATEST));
            assertEquals("export redirect has no valid location", error.getMessage());
        }
    }

    @Test
    void reportsARollupInProgress() throws Exception {
        try (TestServer server = new TestServer(exchange -> {
                    exchange.getResponseHeaders().set("Retry-After", "60");
                    TestServer.json(exchange, 404, "{\"detail\":\"daily export is being built\"}");
                });
                SynthientClient client = server.client()) {
            ApiException error =
                    assertThrows(ApiException.class, () -> client.proxies().download("2026-05-03"));
            assertEquals(404, error.statusCode());
            assertEquals(Duration.ofMinutes(1), error.retryAfter());
            assertEquals(1, server.requests.size());
        }
    }

    @Test
    void requiresARedirect() throws Exception {
        try (TestServer server = new TestServer(exchange -> TestServer.json(exchange, 200, "{}"));
                SynthientClient client = server.client()) {
            SynthientException error = assertThrows(
                    SynthientException.class, () -> client.proxies().downloadUrl(ExportId.LATEST));
            assertEquals("expected a redirect to the export, got status 200", error.getMessage());
        }
    }

    @Test
    void storageErrorsKeepOnlyTheStatus() throws Exception {
        try (TestServer server = server(
                        "/storage/x?X-Amz-Signature=" + SIGNATURE,
                        exchange ->
                                TestServer.respond(exchange, 403, "text/plain", "<Error>" + SIGNATURE + "</Error>"));
                SynthientClient client = server.client()) {
            ApiException error =
                    assertThrows(ApiException.class, () -> client.proxies().download(ExportId.LATEST));
            assertEquals(403, error.statusCode());
            assertEquals("", error.detail());
            assertNoSignature(error);
        }
    }

    @Test
    void unreachableStorageLeaksNoUrl() throws Exception {
        String location = "http://127.0.0.1:" + TestServer.closedPort() + "/x?X-Amz-Signature=" + SIGNATURE;
        try (TestServer server = server(location, PARQUET);
                SynthientClient client = server.client()) {
            ApiConnectionException error = assertThrows(
                    ApiConnectionException.class, () -> client.proxies().download(ExportId.LATEST));
            assertNoSignature(error);
        }
    }

    private static void assertNoSignature(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            assertFalse(String.valueOf(cause).contains(SIGNATURE), cause.toString());
            for (Throwable suppressed : cause.getSuppressed()) {
                assertFalse(String.valueOf(suppressed).contains(SIGNATURE), suppressed.toString());
            }
        }
    }
}
