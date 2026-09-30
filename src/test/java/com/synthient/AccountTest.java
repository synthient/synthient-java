package com.synthient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.synthient.model.Account;
import com.synthient.model.Relation;
import com.synthient.model.Scope;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AccountTest {
    static final String ACCOUNT = """
            {
              "first_name": "Ada", "last_name": "Lovelace", "email": "ada@example.com",
              "organization": {"id": "7f7c2a36-8a4e-4c55-9a0f-3f1e4a2b9c10", "name": "Analytical", "relation": "OWNER"},
              "scopes": ["BASIC", "PROXY_FEEDS"],
              "lookup_quota": {"credits": 9000000000, "resets_in": 86400},
              "api_key": {"id": "5a1c3e2f-0000-4000-8000-000000000001", "name": "sdk",
                          "created_at": "2026-09-28T19:04:52.463869Z"}
            }
            """;

    @Test
    void decodesTheAccount() throws Exception {
        try (TestServer server = new TestServer(exchange -> TestServer.json(exchange, 200, ACCOUNT));
                SynthientClient client = server.client()) {
            Account account = client.account();
            assertEquals("/api/v4/account/me", server.requests.getFirst().path());
            assertEquals("Ada", account.firstName());
            assertEquals(Relation.OWNER, account.organization().relation());
            assertEquals(List.of(Scope.BASIC, Scope.PROXY_FEEDS), account.scopes());
            assertEquals(9_000_000_000L, account.lookupQuota().credits());
            assertEquals(Duration.ofDays(1), account.lookupQuota().resetsIn());
            assertNotNull(account.apiKey());
            assertEquals(
                    Instant.parse("2026-09-28T19:04:52.463869Z"),
                    account.apiKey().createdAt());
        }
    }

    @Test
    void apiKeyIsOptional() throws Exception {
        String body = ACCOUNT.replaceAll("(?s),\\s*\"api_key\".*\\}\\s*\\}", "}");
        try (TestServer server = new TestServer(exchange -> TestServer.json(exchange, 200, body));
                SynthientClient client = server.client()) {
            assertNull(client.account().apiKey());
        }
    }

    @Test
    void healthSucceeds() throws Exception {
        try (TestServer server = new TestServer(exchange -> TestServer.json(exchange, 200, "{\"status\":\"ok\"}"));
                SynthientClient client = server.client()) {
            client.health();
            assertEquals(List.of("/api/v4/health"), server.paths());
        }
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "503 | application/json | {\"status\":\"unhealthy\",\"problems\":[\"db\"]}",
                "503 | text/html | <html>down</html>",
                "401 | text/plain | 401 invalid api key"
            })
    void healthFailuresAreNotRetried(int status, String contentType, String body) throws Exception {
        try (TestServer server = new TestServer(exchange -> TestServer.respond(exchange, status, contentType, body));
                SynthientClient client = server.client()) {
            ApiException error = assertThrows(ApiException.class, client::health);
            assertEquals(status, error.statusCode());
            assertEquals(1, server.requests.size());
        }
    }
}
