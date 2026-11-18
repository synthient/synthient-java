package com.synthient;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.synthient.model.Export;
import com.synthient.model.ExportEntity;
import com.synthient.model.ExportFormat;
import com.synthient.model.ExportKind;
import com.synthient.model.ExportMeta;
import com.synthient.model.ExportPage;
import com.synthient.model.ExportVariant;
import com.synthient.model.ExportVolume;
import com.synthient.model.ParquetColumn;
import com.synthient.model.WhoisEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class FeedTest {
    private static String export(String id) {
        return """
                {"id": "%s", "kind": "hourly", "date": "2026-05-03", "hour": 7, "size_bytes": 10, "row_count": 2,
                 "checksum": "ab", "created_at": 1777777777, "download_path": "/api/v4/feeds/proxies/export/%s",
                 "formats": [{"format": "parquet", "size_bytes": 10, "checksum": "ab"}]}
                """.formatted(id, id);
    }

    private static String page(String cursor, String... ids) {
        List<String> exports = new ArrayList<>();
        for (String id : ids) {
            exports.add(export(id));
        }
        String next = cursor == null ? "" : ",\"next_cursor\":\"" + cursor + "\"";
        return "{\"stream\":\"proxies\",\"feeds\":[" + String.join(",", exports) + "]" + next + "}";
    }

    @ParameterizedTest
    @CsvSource({
        "latest, latest",
        "2026-05-03, 2026-05-03",
        "2026-05-03/7, 2026-05-03/7",
        "2026-05-03/07, 2026-05-03/7",
        "2026-05-03/0, 2026-05-03/0",
        "2026-05-03/23, 2026-05-03/23",
        "2024-02-29, 2024-02-29"
    })
    void normalizesValidIds(String id, String path) {
        assertEquals(path, Exports.exportPath(id, true));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "latest/3",
                "2026-05-03/24",
                "2026-05-03/",
                "/24",
                "../account/me",
                "2026-05-03/07/meta",
                "2026-02-30",
                "2026-5-3",
                "2026-05-03/123",
                " 2026-05-03"
            })
    void rejectsInvalidIds(String id) {
        assertThrows(IllegalArgumentException.class, () -> Exports.exportPath(id, true));
    }

    @Test
    void buildsIds() {
        assertEquals("2026-05-03", ExportId.daily(LocalDate.of(2026, 5, 3)));
        assertEquals("2026-05-03/4", ExportId.hourly(LocalDate.of(2026, 5, 3), 4));
        assertThrows(IllegalArgumentException.class, () -> ExportId.hourly(LocalDate.of(2026, 5, 3), 24));
        assertThrows(IllegalArgumentException.class, () -> ExportId.hourly(LocalDate.of(2026, 5, 3), -1));
        assertEquals("latest", ExportId.LATEST);
    }

    @Test
    void routesEveryFeed() throws Exception {
        try (TestServer server = new TestServer(exchange -> TestServer.json(exchange, 404, "{}"));
                SynthientClient client = server.client()) {
            for (Feed<?> feed : TestServer.feeds(client)) {
                assertThrows(ApiException.class, () -> feed.exportMeta(ExportId.LATEST));
                assertThrows(ApiException.class, () -> feed.exportMeta("2026-05-03/07"));
            }
            List<String> expected = new ArrayList<>();
            for (String feed : List.of(
                    "proxies",
                    "anonymizers",
                    "torrents",
                    "ja4t",
                    "helios/http",
                    "helios/https",
                    "helios/dns",
                    "helios/adb")) {
                expected.add("/api/v4/feeds/" + feed + "/export/latest/meta");
                expected.add("/api/v4/feeds/" + feed + "/export/2026-05-03/7/meta");
            }
            assertEquals(expected, server.paths());
        }
    }

    @Test
    void invalidIdsSendNothing() throws Exception {
        try (TestServer server = new TestServer(exchange -> TestServer.json(exchange, 404, "{}"));
                SynthientClient client = server.client()) {
            String invalid = "../account/me";
            assertThrows(IllegalArgumentException.class, () -> client.proxies().exportMeta(invalid));
            assertThrows(IllegalArgumentException.class, () -> client.proxies().downloadUrl(invalid));
            assertThrows(IllegalArgumentException.class, () -> client.proxies().download(invalid));
            assertTrue(server.requests.isEmpty());
        }
    }

    @Test
    void listsExports() throws Exception {
        String body = page("c2", "2026-05-03/7")
                .replace("\"hour\": 7", "\"hour\": null")
                .replace("\"kind\": \"hourly\"", "\"kind\": \"daily\"");
        try (TestServer server = new TestServer(exchange -> TestServer.json(exchange, 200, body));
                SynthientClient client = server.client()) {
            ExportPage page = client.proxies().listExports(2, "c 1");
            assertEquals("cursor=c%201&limit=2", server.requests.getFirst().query());
            assertEquals("c2", page.nextCursor());
            Export export = page.exports().getFirst();
            assertEquals("2026-05-03/7", export.id());
            assertEquals(ExportKind.DAILY, export.kind());
            assertEquals(LocalDate.of(2026, 5, 3), export.date());
            assertNull(export.hour());
            assertEquals(Instant.ofEpochSecond(1777777777), export.createdAt());
            client.proxies().listExports();
            assertEquals("", server.requests.get(1).query());
        }
    }

    @Test
    void treatsAnEmptyCursorAsTheLastPage() throws Exception {
        try (TestServer server = new TestServer(exchange -> TestServer.json(exchange, 200, page("")));
                SynthientClient client = server.client()) {
            assertNull(client.proxies().listExports().nextCursor());
        }
    }

    @Test
    void rejectsInvalidLimits() throws Exception {
        try (TestServer server = new TestServer(exchange -> TestServer.json(exchange, 200, page(null)));
                SynthientClient client = server.client()) {
            assertThrows(IllegalArgumentException.class, () -> client.proxies().listExports(0, null));
            assertThrows(IllegalArgumentException.class, () -> client.proxies().allExports(0, null));
            assertTrue(server.requests.isEmpty());
        }
    }

    private static TestServer pages(Function<String, String> byCursor) throws Exception {
        return new TestServer(exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            String cursor =
                    query == null || !query.startsWith("cursor=") ? "" : query.replaceAll("cursor=([^&]*).*", "$1");
            TestServer.json(exchange, 200, byCursor.apply(cursor));
        });
    }

    @Test
    void iteratesEveryPage() throws Exception {
        try (TestServer server = pages(cursor -> switch (cursor) {
                    case "" -> page("c1", "a", "b");
                    case "c1" -> page("c2");
                    default -> page(null, "c");
                });
                SynthientClient client = server.client()) {
            List<String> ids = new ArrayList<>();
            for (Export export : client.proxies().allExports(2, null)) {
                ids.add(export.id());
            }
            assertEquals(List.of("a", "b", "c"), ids);
            assertEquals(
                    List.of("limit=2", "cursor=c1&limit=2", "cursor=c2&limit=2"),
                    server.requests.stream().map(TestServer.Request::query).toList());
        }
    }

    @Test
    void fetchesLazily() throws Exception {
        try (TestServer server = pages(cursor -> page("c1", "a", "b"));
                SynthientClient client = server.client()) {
            Iterable<Export> exports = client.proxies().allExports();
            assertTrue(server.requests.isEmpty());
            Iterator<Export> iterator = exports.iterator();
            assertTrue(iterator.hasNext());
            assertTrue(iterator.hasNext());
            assertEquals("a", iterator.next().id());
            assertEquals(1, server.requests.size());
            assertEquals("a", exports.iterator().next().id());
            assertEquals(2, server.requests.size());
        }
    }

    @Test
    void failsOnARepeatedCursor() throws Exception {
        try (TestServer server = pages(cursor -> page("c1", "x" + cursor));
                SynthientClient client = server.client()) {
            Iterator<Export> iterator = client.proxies().allExports().iterator();
            assertEquals("x", iterator.next().id());
            assertEquals("xc1", iterator.next().id());
            SynthientException error = assertThrows(SynthientException.class, iterator::hasNext);
            assertEquals("export listing returned a repeated cursor", error.getMessage());
            assertEquals(2, server.requests.size());
        }
    }

    @Test
    void failsOnARepeatedStartingCursor() throws Exception {
        try (TestServer server = pages(cursor -> page("start", "a"));
                SynthientClient client = server.client()) {
            Iterator<Export> iterator =
                    client.proxies().allExports(100, "start").iterator();
            assertEquals("a", iterator.next().id());
            assertThrows(SynthientException.class, iterator::hasNext);
        }
    }

    @Test
    void surfacesALaterPageFailure() throws Exception {
        try (TestServer server = new TestServer(exchange -> {
                    if (exchange.getRequestURI().getRawQuery() == null) {
                        TestServer.json(exchange, 200, page("c1", "a"));
                    } else {
                        TestServer.json(exchange, 403, "{\"detail\":\"needs PROXY_FEEDS\"}");
                    }
                });
                SynthientClient client = server.client()) {
            Iterator<Export> iterator = client.proxies().allExports().iterator();
            assertEquals("a", iterator.next().id());
            ApiException error = assertThrows(ApiException.class, iterator::hasNext);
            assertEquals(403, error.statusCode());
            assertFalse(server.requests.isEmpty());
        }
    }

    @Test
    void canRetryAFailedPageWithoutRepeatingEarlierExports() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        try (TestServer server = new TestServer(exchange -> {
                    if (exchange.getRequestURI().getRawQuery() == null) {
                        TestServer.json(exchange, 200, page("c1", "a"));
                    } else if (attempts.getAndIncrement() == 0) {
                        TestServer.json(exchange, 503, "{}");
                    } else {
                        TestServer.json(exchange, 200, page(null, "b"));
                    }
                });
                SynthientClient client = server.clientBuilder().maxRetries(0).build()) {
            Iterator<Export> iterator = client.proxies().allExports().iterator();
            assertEquals("a", iterator.next().id());
            assertThrows(ApiException.class, iterator::hasNext);
            assertEquals("b", iterator.next().id());
            assertFalse(iterator.hasNext());
            assertEquals(3, server.requests.size());
        }
    }

    @Test
    void filtersListsByDayAndVariant() throws Exception {
        try (TestServer server = new TestServer(exchange -> TestServer.json(exchange, 200, page(null)));
                SynthientClient client = server.client()) {
            client.proxies().listExports(2, null, ExportId.daily(LocalDate.of(2026, 5, 3)), ExportVariant.LITE);
            client.proxies().listExports(2, "c1", null, null);
            assertEquals(
                    List.of("limit=2&day=2026-05-03&variant=lite", "cursor=c1&limit=2"),
                    server.requests.stream().map(TestServer.Request::query).toList());
        }
    }

    @Test
    void sendsTheDayAndVariantOnEveryPage() throws Exception {
        try (TestServer server = pages(cursor -> cursor.isEmpty() ? page("c1", "a") : page(null, "b"));
                SynthientClient client = server.client()) {
            List<String> ids = new ArrayList<>();
            for (Export export : client.proxies().allExports(1, null, "2026-05-03", ExportVariant.LITE)) {
                ids.add(export.id());
            }
            assertEquals(List.of("a", "b"), ids);
            assertEquals(
                    List.of("limit=1&day=2026-05-03&variant=lite", "cursor=c1&limit=1&day=2026-05-03&variant=lite"),
                    server.requests.stream().map(TestServer.Request::query).toList());
        }
    }

    @Test
    void fetchesTheExportVolume() throws Exception {
        String body = """
                {"stream": "proxies", "days": [
                  {"date": "2026-11-17", "kind": "daily", "snapshots": 1, "row_count": 1528344568,
                   "size_bytes": 20640294576},
                  {"date": "2026-11-18", "kind": "hourly", "snapshots": 3, "row_count": 6441725000,
                   "size_bytes": 880000000}]}
                """;
        try (TestServer server = new TestServer(exchange -> TestServer.json(exchange, 200, body));
                SynthientClient client = server.client()) {
            ExportVolume volume = client.heliosAdb().exportVolume();
            client.proxies().exportVolume(7);
            assertEquals(
                    "/api/v4/feeds/helios/adb/export/daily",
                    server.requests.get(0).path());
            assertEquals("", server.requests.get(0).query());
            assertEquals(
                    "/api/v4/feeds/proxies/export/daily", server.requests.get(1).path());
            assertEquals("days=7", server.requests.get(1).query());
            assertEquals("proxies", volume.stream());
            assertEquals(LocalDate.of(2026, 11, 17), volume.days().get(0).date());
            assertEquals(ExportKind.DAILY, volume.days().get(0).kind());
            assertEquals(20_640_294_576L, volume.days().get(0).sizeBytes());
            assertEquals(ExportKind.HOURLY, volume.days().get(1).kind());
            assertEquals(3, volume.days().get(1).snapshots());
            assertEquals(6_441_725_000L, volume.days().get(1).rowCount());
        }
    }

    @Test
    void sendsTheVariantForMetadata() throws Exception {
        try (TestServer server = new TestServer(
                        exchange -> TestServer.json(exchange, 404, "{\"detail\":\"No snapshot found\"}"));
                SynthientClient client = server.client()) {
            ApiException error = assertThrows(
                    ApiException.class, () -> client.proxies().exportMeta(ExportId.LATEST, ExportVariant.LITE));
            assertEquals(404, error.statusCode());
            assertThrows(
                    ApiException.class, () -> client.anonymizers().exportMeta("2026-05-03/07", ExportVariant.LITE));
            assertThrows(ApiException.class, () -> client.proxies().exportMeta(ExportId.LATEST));
            assertEquals(
                    List.of(
                            "/api/v4/feeds/proxies/export/latest/meta?variant=lite",
                            "/api/v4/feeds/anonymizers/export/2026-05-03/7/meta?variant=lite",
                            "/api/v4/feeds/proxies/export/latest/meta?"),
                    server.requests.stream()
                            .map(r -> r.path() + "?" + r.query())
                            .toList());
        }
    }

    @Test
    void decodesExportFormatsEntitiesAndVariant() {
        Export lite = Json.decode(
                export("2026-05-03")
                        .replace("\"hour\": 7,", "\"variant\": \"lite\",")
                        .getBytes(UTF_8),
                Export.class);
        assertEquals(ExportVariant.LITE, lite.variant());
        assertNull(lite.entities());
        assertEquals(ExportFormat.PARQUET, lite.formats().getFirst().format());

        Export whois = Json.decode("""
                {"id": "2026-11-17", "kind": "daily", "date": "2026-11-17", "size_bytes": 997234948,
                 "row_count": 6583438, "checksum": "add0", "created_at": 1794960929,
                 "download_path": "/api/v4/feeds/whois/export/2026-11-17",
                 "formats": [{"format": "tar.gz", "size_bytes": 997234948, "checksum": "add0"}],
                 "entities": [{"entity": "asns", "row_count": 123778, "size_bytes": 5442736, "checksum": "062f"},
                              {"entity": "registrars", "row_count": 1, "size_bytes": 2, "checksum": "ff"}]}
                """.getBytes(UTF_8), Export.class);
        assertNull(whois.variant());
        assertEquals(ExportFormat.TAR_GZ, whois.formats().getFirst().format());
        assertNotNull(whois.entities());
        ExportEntity asns = whois.entities().getFirst();
        assertEquals(WhoisEntity.ASNS, asns.entity());
        assertEquals(123_778, asns.rowCount());
        assertNull(asns.schema());
        assertEquals("registrars", whois.entities().get(1).entity());
    }

    private static String meta(String format, String schema) {
        return """
                {"id": "latest", "stream": "whois", "kind": "daily", "format": "%s", "date": 1794873600,
                 "rows": 23481094, "size": 997234948, "checksum": "add0", "created_at": 1794960929%s,
                 "formats": [{"format": "%s", "size_bytes": 997234948, "checksum": "add0"}]}
                """.formatted(format, schema, format);
    }

    @ParameterizedTest
    @ValueSource(strings = {"parquet", "csv", "mmdb", "tar.gz", "zip"})
    void decodesEveryMetaFormat(String format) {
        ExportMeta meta =
                Json.decode(meta(format, ",\"schema\":{\"fields\":[]}").getBytes(UTF_8), ExportMeta.class);
        assertEquals(format, meta.format());
        assertEquals(format, meta.formats().getFirst().format());
        assertNull(meta.variant());
    }

    @ParameterizedTest
    @ValueSource(strings = {",\"schema\":{\"fields\":[]}", ",\"schema\":{\"fields\":null}", ",\"schema\":{}"})
    void decodesAnArchiveSchemaAsEmpty(String schema) {
        String body = meta(ExportFormat.TAR_GZ, schema).replace("\"formats\"", """
                        "entities": [{"entity": "contacts", "row_count": 6459660, "size_bytes": 205447373,
                          "checksum": "cf61", "schema": {"fields": [{"name": "emails", "type": "list<string>"}]}}],
                        "formats\"""");
        ExportMeta meta = Json.decode(body.getBytes(UTF_8), ExportMeta.class);
        assertEquals(List.of(), meta.schema().fields());
        assertNotNull(meta.entities());
        ExportEntity contacts = meta.entities().getFirst();
        assertEquals(WhoisEntity.CONTACTS, contacts.entity());
        assertNotNull(contacts.schema());
        assertEquals(
                new ParquetColumn("emails", "list<string>"),
                contacts.schema().fields().getFirst());
    }

    @Test
    void decodesColumnTypesTheSpecDoesNotList() {
        List<String> types = List.of("double", "int32", "date", "float", "timestamp", "list<string>");
        StringBuilder fields = new StringBuilder();
        for (String type : types) {
            fields.append(fields.isEmpty() ? "" : ",")
                    .append("{\"name\":\"c\",\"type\":\"")
                    .append(type)
                    .append("\"}");
        }
        ExportMeta meta = Json.decode(
                meta(ExportFormat.PARQUET, ",\"schema\":{\"fields\":[" + fields + "]}")
                        .getBytes(UTF_8),
                ExportMeta.class);
        assertEquals(
                types, meta.schema().fields().stream().map(ParquetColumn::type).toList());
    }

    @Test
    void requiresExportFormats() {
        String body = export("latest").replaceAll("(?s),\\s*\"formats\".*]", "");
        assertThrows(SynthientException.class, () -> Json.decode(body.getBytes(UTF_8), Export.class));
    }
}
