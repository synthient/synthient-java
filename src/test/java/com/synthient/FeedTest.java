package com.synthient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.synthient.model.Export;
import com.synthient.model.ExportKind;
import com.synthient.model.ExportPage;
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
                 "checksum": "ab", "created_at": 1777777777, "download_path": "/api/v4/feeds/proxies/export/%s"}
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
        assertEquals(path, Feed.exportPath(id));
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
        assertThrows(IllegalArgumentException.class, () -> Feed.exportPath(id));
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
                    "helio/http",
                    "helio/https",
                    "helio/dns",
                    "helio/adb")) {
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
}
