package com.synthient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.synthient.model.DomainInfo;
import com.synthient.model.IpInfo;
import com.synthient.model.NetworkType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class LookupTest {
    @Test
    void decodesAnIpLookup() throws Exception {
        try (TestServer server = new TestServer(exchange -> TestServer.json(exchange, 200, TestServer.IP_INFO));
                SynthientClient client = server.client()) {
            IpInfo info = client.lookupIp("8.8.8.8");
            assertEquals("8.8.8.8", info.ip());
            assertNotNull(info.network());
            assertEquals(15169, info.network().asn());
            assertEquals(NetworkType.DATACENTER, info.network().type());
            assertNull(info.network().org());
            assertNull(info.network().abusePhone());
            assertEquals("network-abuse@google.com", info.network().abuseEmail());
            assertNotNull(info.location());
            assertEquals(-122.0775, info.location().longitude());
            assertNotNull(info.intelligence());
            assertEquals(12, info.intelligence().riskScore());
            assertEquals(List.of("DATACENTER"), info.intelligence().categories());
            assertEquals("google", info.intelligence().providers().getFirst().name());
            assertEquals(
                    Instant.ofEpochSecond(1780000000),
                    info.intelligence().providers().getFirst().lastSeen());
            assertEquals("Linux", info.intelligence().devices().getFirst().os());
        }
    }

    @Test
    void absentSectionsAreNull() throws Exception {
        try (TestServer server = new TestServer(exchange -> TestServer.json(exchange, 200, "{\"ip\":\"10.0.0.1\"}"));
                SynthientClient client = server.client()) {
            IpInfo info = client.lookupIp("10.0.0.1");
            assertNull(info.network());
            assertNull(info.location());
            assertNull(info.intelligence());
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"ip\":null}",
                "{\"ip\":\"1.1.1.1\",\"network\":{\"isp\":\"x\",\"type\":\"MOBILE\"}}",
                "{\"ip\":\"1.1.1.1\",\"network\":{\"asn\":\"15169\",\"isp\":\"x\",\"type\":\"MOBILE\"}}",
                "{\"ip\":\"1.1.1.1\",\"network\":{\"asn\":1.5,\"isp\":\"x\",\"type\":\"MOBILE\"}}",
                "{\"ip\":15169}",
                "[]",
                "null",
                "",
                "{\"ip\":\"1.1.1.1\"} {}",
                "{\"ip\":\"1.1.1.1\""
            })
    void rejectsInvalidResponses(String body) throws Exception {
        try (TestServer server = new TestServer(exchange -> TestServer.json(exchange, 200, body));
                SynthientClient client = server.client()) {
            SynthientException error = assertThrows(SynthientException.class, () -> client.lookupIp("1.1.1.1"));
            assertEquals(SynthientException.class, error.getClass());
        }
    }

    @ParameterizedTest
    @CsvSource({
        "8.8.8.8, /api/v4/lookup/ip/8.8.8.8",
        "2001:db8::1, /api/v4/lookup/ip/2001:db8::1",
        "::ffff:1.2.3.4, /api/v4/lookup/ip/::ffff:1.2.3.4"
    })
    void escapesAddresses(String ip, String path) throws Exception {
        try (TestServer server = new TestServer(exchange -> TestServer.json(exchange, 200, "{\"ip\":\"x\"}"));
                SynthientClient client = server.client()) {
            client.lookupIp(ip);
            assertEquals(List.of(path), server.paths());
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "cafe.be",
                "example.com",
                "256.1.1.1",
                "01.1.1.1",
                "1.1.1",
                "1.1.1.1 ",
                "[::1]",
                "fe80::1%en0",
                "gg::1",
                ".:1",
                "::1/128"
            })
    void invalidAddressesSendNothing(String ip) throws Exception {
        try (TestServer server = new TestServer(exchange -> TestServer.json(exchange, 200, "{}"));
                SynthientClient client = server.client()) {
            assertThrows(IllegalArgumentException.class, () -> client.lookupIp(ip));
            assertThrows(IllegalArgumentException.class, () -> client.lookupIps(List.of("1.1.1.1", ip)));
            assertTrue(server.requests.isEmpty());
        }
    }

    @Test
    void batchLookup() throws Exception {
        String body = "{\"results\":[" + TestServer.IP_INFO + ",{\"ip\":\"1.1.1.1\"}]}";
        try (TestServer server = new TestServer(exchange -> TestServer.json(exchange, 200, body));
                SynthientClient client = server.client()) {
            List<IpInfo> results = client.lookupIps(List.of("8.8.8.8", "1.1.1.1"));
            assertEquals(
                    List.of("8.8.8.8", "1.1.1.1"),
                    results.stream().map(IpInfo::ip).toList());
            TestServer.Request request = server.requests.getFirst();
            assertEquals("POST", request.method());
            assertEquals("/api/v4/lookup/ips", request.path());
            assertEquals("application/json", request.header("Content-Type"));
            assertEquals("{\"ips\":[\"8.8.8.8\",\"1.1.1.1\"]}", request.body());
        }
    }

    @Test
    void batchRequiresResults() throws Exception {
        try (TestServer server = new TestServer(exchange -> TestServer.json(exchange, 200, "{}"));
                SynthientClient client = server.client()) {
            assertThrows(SynthientException.class, () -> client.lookupIps(List.of("8.8.8.8")));
        }
    }

    @Test
    void batchLimits() throws Exception {
        try (TestServer server = new TestServer(exchange -> TestServer.json(exchange, 200, "{}"));
                SynthientClient client = server.client()) {
            List<String> tooMany = Collections.nCopies(1001, "1.1.1.1");
            IllegalArgumentException error =
                    assertThrows(IllegalArgumentException.class, () -> client.lookupIps(tooMany));
            assertEquals("1001 addresses exceed the batch limit of 1000", error.getMessage());
            List<String> invalid = new ArrayList<>(List.of("1.1.1.1", "2.2.2.2", "nope"));
            error = assertThrows(IllegalArgumentException.class, () -> client.lookupIps(invalid));
            assertEquals("invalid IP address at index 2", error.getMessage());
            List<String> withNull = new ArrayList<>(List.of("1.1.1.1"));
            withNull.add(null);
            error = assertThrows(IllegalArgumentException.class, () -> client.lookupIps(withNull));
            assertEquals("invalid IP address at index 1", error.getMessage());
            assertTrue(server.requests.isEmpty());
        }
    }

    @ParameterizedTest
    @CsvSource({
        "example.com, /api/v4/lookup/domain/example.com",
        "a/b?c, /api/v4/lookup/domain/a%2Fb%3Fc",
        "a b#c%, /api/v4/lookup/domain/a%20b%23c%25",
        "bücher.de, /api/v4/lookup/domain/b%C3%BCcher.de"
    })
    void escapesDomains(String domain, String path) throws Exception {
        try (TestServer server = new TestServer(exchange -> TestServer.json(exchange, 200, DOMAIN_INFO));
                SynthientClient client = server.client()) {
            client.lookupDomain(domain);
            assertEquals(List.of(path), server.paths());
            assertEquals("", server.requests.getFirst().query());
        }
    }

    @Test
    void decodesADomainLookup() throws Exception {
        try (TestServer server = new TestServer(exchange -> TestServer.json(exchange, 200, DOMAIN_INFO));
                SynthientClient client = server.client()) {
            DomainInfo info = client.lookupDomain("example.com", 5);
            assertEquals("limit=5", server.requests.getFirst().query());
            assertEquals(1200, info.stats().events24h());
            assertEquals(List.of(3L, 4L), info.uniqueIps().sparkline24h());
            assertEquals(List.of(List.of(1L, 2L), List.of(3L)), info.hourDowHeatmap());
            assertEquals("203.0.113.9", info.recentEvents().getFirst().sourceIp());
            assertEquals(
                    Instant.ofEpochSecond(1780000000),
                    info.timeSeries().getFirst().date());
            assertNull(info.topAsn());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ".", ".."})
    void invalidDomainsSendNothing(String domain) throws Exception {
        try (TestServer server = new TestServer(exchange -> TestServer.json(exchange, 200, DOMAIN_INFO));
                SynthientClient client = server.client()) {
            assertThrows(IllegalArgumentException.class, () -> client.lookupDomain(domain));
            assertThrows(IllegalArgumentException.class, () -> client.lookupDomain("example.com", 0));
            assertTrue(server.requests.isEmpty());
        }
    }

    @Test
    void modelsAreImmutable() throws Exception {
        try (TestServer server = new TestServer(exchange -> TestServer.json(exchange, 200, TestServer.IP_INFO));
                SynthientClient client = server.client()) {
            IpInfo info = client.lookupIp("8.8.8.8");
            assertNotNull(info.intelligence());
            List<String> categories = info.intelligence().categories();
            assertInstanceOf(
                    UnsupportedOperationException.class,
                    assertThrows(RuntimeException.class, () -> categories.add("x")));
        }
    }

    static final String DOMAIN_INFO = """
            {
              "domain": "example.com",
              "status": "active",
              "stats": {"total_events_30d": 50000, "events_24h": 1200},
              "time_series": [{"date": 1780000000, "events": 10, "unique_ips": 4}],
              "unique_ips": {"value_24h": 7, "value_30d": 90, "sparkline_24h": [3, 4]},
              "top_subdomains": [{"subdomain": "www", "count": 12}],
              "top_ports": [{"port": 443, "count": 12}],
              "geo_distribution": [{"country_code": "US", "unique_ips": 3, "events": 9}],
              "hour_dow_heatmap": [[1, 2], [3]],
              "activity_stats": {"peak_hour": 14, "quiet_hour": 3, "median_per_hour": 5, "p95_per_hour": 20,
                                 "cadence": "Activity is evenly distributed across the day."},
              "recent_events": [{"timestamp": 1780000500, "source_ip_masked": "203.0.113.9",
                                 "target_subdomain": "www", "port": 443, "country_code": "US"}]
            }
            """;
}
