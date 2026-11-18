package com.synthient;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.synthient.model.Export;
import com.synthient.model.ExportFormat;
import java.io.InputStream;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DatasetTest {
    private static final String PAGE = """
            {"stream": "geolocation", "feeds": [{"id": "2026-11-17", "kind": "daily", "date": "2026-11-17",
              "size_bytes": 466431938, "row_count": 27456467, "checksum": "9712", "created_at": 1794960045,
              "download_path": "/api/v4/feeds/geolocation/export/2026-11-17",
              "formats": [{"format": "parquet", "size_bytes": 466431938, "checksum": "9712"},
                          {"format": "csv", "size_bytes": 540196440, "checksum": "de74"},
                          {"format": "mmdb", "size_bytes": 1874587084, "checksum": "ae71"}]}]}
            """;

    @Test
    void routesBothDatasets() throws Exception {
        try (TestServer server = new TestServer(exchange -> TestServer.json(exchange, 404, "{}"));
                SynthientClient client = server.client()) {
            for (Dataset dataset : TestServer.datasets(client)) {
                assertThrows(ApiException.class, dataset::listExports);
                assertThrows(ApiException.class, dataset::exportVolume);
                assertThrows(ApiException.class, () -> dataset.exportMeta(ExportId.LATEST));
                assertThrows(ApiException.class, () -> dataset.downloadUrl("2026-05-03"));
            }
            List<String> expected = new ArrayList<>();
            for (String dataset : List.of("geolocation", "whois")) {
                expected.add("/api/v4/feeds/" + dataset + "/export");
                expected.add("/api/v4/feeds/" + dataset + "/export/daily");
                expected.add("/api/v4/feeds/" + dataset + "/export/latest/meta");
                expected.add("/api/v4/feeds/" + dataset + "/export/2026-05-03");
            }
            assertEquals(expected, server.paths());
        }
    }

    @Test
    void sendsOptionsOnlyWhenSet() throws Exception {
        try (TestServer server = new TestServer(exchange -> TestServer.json(exchange, 404, "{}"));
                SynthientClient client = server.client()) {
            assertThrows(ApiException.class, () -> client.geolocation().exportMeta(ExportId.LATEST, ExportFormat.MMDB));
            assertThrows(ApiException.class, () -> client.whois().exportMeta("2026-05-03", ExportFormat.TAR_GZ));
            assertThrows(ApiException.class, () -> client.geolocation().downloadUrl(ExportId.LATEST, ExportFormat.CSV));
            assertThrows(ApiException.class, () -> client.geolocation().download(ExportId.LATEST));
            assertThrows(ApiException.class, () -> client.geolocation().listExports(10, null, "2026-05-03"));
            assertThrows(ApiException.class, () -> client.whois().listExports(10, "c1"));
            assertThrows(ApiException.class, () -> client.whois().exportVolume(30));
            assertEquals(
                    List.of(
                            "/api/v4/feeds/geolocation/export/latest/meta?format=mmdb",
                            "/api/v4/feeds/whois/export/2026-05-03/meta?format=tar.gz",
                            "/api/v4/feeds/geolocation/export/latest?format=csv",
                            "/api/v4/feeds/geolocation/export/latest?",
                            "/api/v4/feeds/geolocation/export?limit=10&day=2026-05-03",
                            "/api/v4/feeds/whois/export?cursor=c1&limit=10",
                            "/api/v4/feeds/whois/export/daily?days=30"),
                    server.requests.stream()
                            .map(r -> r.path() + "?" + r.query())
                            .toList());
        }
    }

    @Test
    void sendsTheDayOnEveryPage() throws Exception {
        try (TestServer server = new TestServer(exchange -> TestServer.json(
                        exchange,
                        200,
                        exchange.getRequestURI().getRawQuery().contains("cursor")
                                ? PAGE
                                : PAGE.replace("]}]}", "]}], \"next_cursor\": \"c1\"}")));
                SynthientClient client = server.client()) {
            List<Export> exports = new ArrayList<>();
            client.geolocation().allExports(1, null, "2026-11-17").forEach(exports::add);
            assertEquals(2, exports.size());
            assertEquals(3, exports.getFirst().formats().size());
            assertEquals(ExportFormat.MMDB, exports.getFirst().formats().get(2).format());
            assertEquals(
                    List.of("limit=1&day=2026-11-17", "cursor=c1&limit=1&day=2026-11-17"),
                    server.requests.stream().map(TestServer.Request::query).toList());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-05-03/7", "2026-05-03/07", "2026-05-03/0", "../account/me", "daily"})
    void rejectsHourlyAndInvalidIdsBeforeAnyRequest(String id) throws Exception {
        try (TestServer server = new TestServer(exchange -> TestServer.json(exchange, 404, "{}"));
                SynthientClient client = server.client()) {
            for (Dataset dataset : TestServer.datasets(client)) {
                assertThrows(IllegalArgumentException.class, () -> dataset.exportMeta(id));
                assertThrows(IllegalArgumentException.class, () -> dataset.downloadUrl(id, ExportFormat.CSV));
                assertThrows(IllegalArgumentException.class, () -> dataset.download(id));
            }
            assertTrue(server.requests.isEmpty());
        }
    }

    @Test
    void acceptsLatestAndDailyIds() {
        assertEquals("latest", Exports.exportPath(ExportId.LATEST, false));
        assertEquals("2026-05-03", Exports.exportPath(ExportId.daily(LocalDate.of(2026, 5, 3)), false));
    }

    @Test
    void downloadsTheFileAsServedWithoutTheApiKey() throws Exception {
        String signature = "secret-signature";
        try (TestServer server = new TestServer(exchange -> {
                    if (exchange.getRequestURI().getPath().startsWith("/api/v4/")) {
                        exchange.getResponseHeaders()
                                .set("Location", "/storage/geolocation.mmdb?X-Amz-Signature=" + signature);
                        TestServer.respond(exchange, 307, "", "");
                    } else {
                        TestServer.respond(exchange, 200, "application/octet-stream", "MMDBdata");
                    }
                });
                SynthientClient client = server.client()) {
            URI url = client.geolocation().downloadUrl(ExportId.LATEST, ExportFormat.MMDB);
            assertEquals("/storage/geolocation.mmdb", url.getPath());
            try (InputStream file = client.geolocation().download(ExportId.LATEST, ExportFormat.MMDB)) {
                assertEquals("MMDBdata", new String(file.readAllBytes(), UTF_8));
            }
            TestServer.Request api = server.requests.get(1);
            TestServer.Request storage = server.requests.get(2);
            assertEquals("/api/v4/feeds/geolocation/export/latest", api.path());
            assertEquals("format=mmdb", api.query());
            assertEquals(TestServer.API_KEY, api.header("X-Api-Key"));
            assertEquals("/storage/geolocation.mmdb", storage.path());
            assertEquals("", storage.header("X-Api-Key"));
        }
    }
}
