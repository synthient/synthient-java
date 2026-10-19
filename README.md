<p align="center">
  <a href="https://synthient.com">
    <img src="assets/logo.svg" height="96" alt="Synthient logo">
  </a>
</p>
<h3 align="center">Synthient</h3>
<p align="center">High-fidelity data on residential proxies, VPNs, and other anonymized IPs.</p>
<p align="center">
  <a href="https://docs.synthient.com">Documentation</a> ·
  <a href="https://javadoc.io/doc/com.synthient/synthient-java">API reference</a> ·
  <a href="https://docs.synthient.com/misc/support">Support</a>
</p>

# synthient-java

`synthient-java` is the official Java SDK for the [Synthient API](https://docs.synthient.com): IP and domain intelligence, account details, and real-time and bulk data feeds.

[![Maven Central](https://img.shields.io/maven-central/v/com.synthient/synthient-java)](https://central.sonatype.com/artifact/com.synthient/synthient-java)
[![test](https://github.com/synthient/synthient-java/actions/workflows/test.yml/badge.svg)](https://github.com/synthient/synthient-java/actions/workflows/test.yml)
[![lint](https://github.com/synthient/synthient-java/actions/workflows/lint.yml/badge.svg)](https://github.com/synthient/synthient-java/actions/workflows/lint.yml)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

## Table of Contents

- [synthient-java](#synthient-java)
  - [SDK Installation](#sdk-installation)
  - [Requirements](#requirements)
  - [API Keys](#api-keys)
  - [Authentication](#authentication)
  - [SDK Example Usage](#sdk-example-usage)
  - [Available Resources and Operations](#available-resources-and-operations)
  - [Streaming](#streaming)
  - [Pagination](#pagination)
  - [Downloads](#downloads)
  - [Retries](#retries)
  - [Timeouts](#timeouts)
  - [Error Handling](#error-handling)
  - [Server Selection](#server-selection)
  - [Custom HTTP Client](#custom-http-client)
  - [Debugging](#debugging)
  - [gRPC Schema](#grpc-schema)
- [Development](#development)
  - [Maturity](#maturity)
  - [Contributions](#contributions)
  - [License](#license)

## SDK Installation

Add `com.synthient:synthient-java` to your build. The [gRPC schema fetcher](#grpc-schema) is a separate artifact, `com.synthient:synthient-java-grpcschema`, with the same version.

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("com.synthient:synthient-java:1.0.0")
    // Only for the gRPC schema fetcher:
    implementation("com.synthient:synthient-java-grpcschema:1.0.0")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'com.synthient:synthient-java:1.0.0'
    // Only for the gRPC schema fetcher:
    implementation 'com.synthient:synthient-java-grpcschema:1.0.0'
}
```

### Maven

```xml
<dependency>
  <groupId>com.synthient</groupId>
  <artifactId>synthient-java</artifactId>
  <version>1.0.0</version>
</dependency>
<!-- Only for the gRPC schema fetcher: -->
<dependency>
  <groupId>com.synthient</groupId>
  <artifactId>synthient-java-grpcschema</artifactId>
  <version>1.0.0</version>
</dependency>
```

## Requirements

Java 21 or later. The SDK depends on OkHttp 5, Jackson 3, and the JSpecify annotations.

## API Keys

Every request needs a Synthient API key. Keys are issued from the Synthient dashboard; if you don't have one, [contact us](mailto:contact@synthient.com). A key's scopes decide what it can call: `BASIC` covers the lookups and `account()`, and each feed has its own scopes, listed under [Feeds](#feeds).

If a request fails even though you are sending a key:

- **401 Unauthorized**: the key is malformed, unknown, or revoked.
- **402 Payment Required**: the team is out of lookup credits.
- **403 Forbidden**: the key lacks the scope for that endpoint or feed.

`account()` shows the key's scopes and the team's remaining credits.

## Authentication

This SDK supports the following security scheme:

| Name     | Type    | Scheme             | Environment Variable |
| -------- | ------- | ------------------ | -------------------- |
| `apiKey` | API key | `x-api-key` header | `SYNTHIENT_API_KEY`  |

`new SynthientClient()` reads the key from `SYNTHIENT_API_KEY`. To pass it yourself, such as from a secret manager, use the constructor that takes a key or `apiKey` on the builder:

```java
SynthientClient client = new SynthientClient(apiKey);
```

Creating a client makes no network calls. To check a key, call `account()`, which costs no credits.

## SDK Example Usage

### Look up an IP address

Get the network, location, and risk intelligence for an IPv4 or IPv6 address. Each lookup costs one credit; failed lookups are free.

```java
import com.synthient.SynthientClient;
import com.synthient.model.Intelligence;
import com.synthient.model.IpInfo;

public class LookupIp {
    public static void main(String[] args) {
        try (SynthientClient client = new SynthientClient()) {
            IpInfo info = client.lookupIp("8.8.8.8");
            Intelligence intelligence = info.intelligence();
            if (intelligence != null) {
                System.out.println(intelligence.riskScore() + " " + intelligence.categories());
            }
        }
    }
}
```

`network()`, `location()`, and `intelligence()` return null when the API has no data for them. A client is safe for concurrent use, so reuse one and close it on shutdown. The examples below reuse `client`, and runnable programs are in [examples](examples/src/main/java/com/synthient/examples).

### Look up many IP addresses

Look up as many as 1000 addresses in one request for `ceil(n * 0.9)` credits. Private and reserved addresses are skipped and duplicates are merged, so match results by `ip()`.

```java
List<IpInfo> results = client.lookupIps(List.of("8.8.8.8", "1.1.1.1"));
for (IpInfo result : results) {
    Intelligence intelligence = result.intelligence();
    if (intelligence != null) {
        System.out.println(result.ip() + " " + intelligence.riskScore());
    }
}
```

### Look up a domain

Get the honeypot traffic observed for a domain and its most recent events. Each lookup costs one credit.

```java
DomainInfo domain = client.lookupDomain("example.com", 10);
System.out.println(domain.status() + " " + domain.stats().events24h());
for (DomainEvent event : domain.recentEvents()) {
    System.out.println(event.timestamp() + " " + event.targetSubdomain() + " " + event.port());
}
```

### Check your account

Get the key's account, scopes, and remaining lookup credits, at no cost.

```java
Account account = client.account();
System.out.println(account.organization().name() + " " + account.scopes() + " " + account.lookupQuota().credits());
```

## Available Resources and Operations

<details open>
<summary>Available methods</summary>

### [SynthientClient](https://javadoc.io/doc/com.synthient/synthient-java/latest/com/synthient/SynthientClient.html)

- [`lookupIp`](https://javadoc.io/doc/com.synthient/synthient-java/latest/com/synthient/SynthientClient.html#lookupIp(java.lang.String)) - Look up an IPv4 or IPv6 address
- [`lookupIps`](https://javadoc.io/doc/com.synthient/synthient-java/latest/com/synthient/SynthientClient.html#lookupIps(java.util.Collection)) - Look up as many as 1000 addresses in one request
- [`lookupDomain`](https://javadoc.io/doc/com.synthient/synthient-java/latest/com/synthient/SynthientClient.html#lookupDomain(java.lang.String,int)) - Look up the honeypot traffic observed for a domain
- [`account`](https://javadoc.io/doc/com.synthient/synthient-java/latest/com/synthient/SynthientClient.html#account()) - Get the key's account, scopes, and remaining lookup credits
- [`health`](https://javadoc.io/doc/com.synthient/synthient-java/latest/com/synthient/SynthientClient.html#health()) - Check that the API and its dependencies are healthy

### [Feeds](https://javadoc.io/doc/com.synthient/synthient-java/latest/com/synthient/Feed.html)

Each feed comes from a method of the client, such as `client.proxies()`, and has the same methods:

- [`stream`](https://javadoc.io/doc/com.synthient/synthient-java/latest/com/synthient/Feed.html#stream()) - Connect to the feed's real-time events
- [`listExports`](https://javadoc.io/doc/com.synthient/synthient-java/latest/com/synthient/Feed.html#listExports(int,java.lang.String)) - List one page of the feed's Parquet exports, newest first
- [`allExports`](https://javadoc.io/doc/com.synthient/synthient-java/latest/com/synthient/Feed.html#allExports()) - Iterate over every export, fetching pages as needed
- [`exportMeta`](https://javadoc.io/doc/com.synthient/synthient-java/latest/com/synthient/Feed.html#exportMeta(java.lang.String)) - Get an export's checksum, size, row count, and Parquet schema
- [`download`](https://javadoc.io/doc/com.synthient/synthient-java/latest/com/synthient/Feed.html#download(java.lang.String)) - Download an export's Parquet file
- [`downloadUrl`](https://javadoc.io/doc/com.synthient/synthient-java/latest/com/synthient/Feed.html#downloadUrl(java.lang.String)) - Get a presigned URL for an export's Parquet file, valid for 24 hours

| Method          | Data                                          | Event type         | Stream scope            | Export scope          |
| --------------- | --------------------------------------------- | ------------------ | ----------------------- | --------------------- |
| `proxies()`     | Residential, mobile, and datacenter proxies   | `ProxyEvent`       | `PROXY_FIREHOSE`        | `PROXY_FEEDS`         |
| `anonymizers()` | VPN and anonymizer IP ranges                  | `AnonymizerEvent`  | `ANONYMIZERS_STREAM`    | `ANONYMIZERS_FEED`    |
| `torrents()`    | BitTorrent DHT and tracker peers              | `TorrentEvent`     | `TORRENTS_STREAM`       | `TORRENTS_FEED`       |
| `ja4t()`        | JA4T TCP fingerprints                         | `Ja4tEvent`        | `JA4T_STREAM`           | `JA4T_FEED`           |
| `heliosHttp()`  | Helios honeypot HTTP requests                 | `HeliosHttpEvent`  | `HONEYPOT_HTTP_STREAM`  | `HONEYPOT_HTTP_FEED`  |
| `heliosHttps()` | Helios honeypot TLS ClientHellos              | `HeliosHttpsEvent` | `HONEYPOT_HTTPS_STREAM` | `HONEYPOT_HTTPS_FEED` |
| `heliosDns()`   | Helios honeypot tunnel destinations           | `HeliosDnsEvent`   | `HONEYPOT_DNS_STREAM`   | `HONEYPOT_DNS_FEED`   |
| `heliosAdb()`   | Helios honeypot Android Debug Bridge commands | `HeliosAdbEvent`   | `HONEYPOT_ADB_STREAM`   | `HONEYPOT_ADB_FEED`   |

</details>

## Streaming

`stream()` connects to a feed's real-time events and returns an `EventStream`, which you iterate over and close to disconnect:

```java
while (true) {
    try (EventStream<ProxyEvent> events = client.proxies().stream()) {
        for (ProxyEvent event : events) {
            System.out.println(event.ip() + " " + event.provider() + " " + event.type());
        }
    } catch (SynthientException e) {
        if (e instanceof ApiException api && (api.statusCode() == 401 || api.statusCode() == 403)) {
            throw e;
        }
        System.err.println("proxy stream: " + e);
        // Avoid reconnecting in a tight loop.
        Thread.sleep(2000);
    }
}
```

- The server closes each stream after up to 30 minutes. That ends the inner loop without an exception, and the outer loop reconnects at once.
- A 401 or 403 stops the loop, because reconnecting can't fix a rejected key or a missing scope.
- Any other failure ends the stream, and the loop waits 2 seconds before reconnecting.
- Events sent while disconnected are missed. The API has no resume cursor.

## Pagination

`listExports(limit, cursor)` returns one page of a feed's exports, newest first. Pass `page.nextCursor()` as the cursor to get the next page; it is null on the last one.

```java
ExportPage page = client.proxies().listExports(50, null);
for (Export export : page.exports()) {
    System.out.println(export.id() + " " + export.kind() + " " + export.rowCount());
}
```

`allExports()` fetches the pages for you as the loop needs them:

```java
for (Export export : client.proxies().allExports()) {
    System.out.println(export.id() + " " + export.kind() + " " + export.rowCount());
}
```

On the first page, the newest hourly export has the ID `latest`, a moving alias for the freshest export. Its `date()` and `hour()` give its stable ID, such as `2026-10-14/21`.

## Downloads

Exports are Parquet files. `download` returns a file's contents as an `InputStream`, which you must close:

```java
try (InputStream body = client.proxies().download(ExportId.LATEST)) {
    Files.copy(body, Path.of("proxies.parquet"), StandardCopyOption.REPLACE_EXISTING);
}
```

- Name an export by its `id()` from a listing, by `ExportId.LATEST`, or by `ExportId.daily(date)` or `ExportId.hourly(date, hour)` for a UTC day or hour.
- `exportMeta` returns the file's SHA-256 checksum. Use a fixed ID for both calls, because `latest` can move between them.
- `downloadUrl` returns a presigned URL that works without a key for 24 hours, so treat it as a secret. Your API key is never sent to the storage host.
- Hourly exports roll into the daily export shortly after 00:30 UTC the next day. After that, the hour returns a 400.

## Retries

The client retries a failed request up to twice, backing off exponentially with jitter and honoring `Retry-After`:

| Requests                      | Retried on                                                                                   |
| ----------------------------- | -------------------------------------------------------------------------------------------- |
| Account and feed requests     | 500, 502, 503, and 504, most connection failures and timeouts, and 429 with `Retry-After`    |
| Billed lookups and `health()` | Only 429 with `Retry-After`, which comes before any work, so a lookup is never charged twice |

A 429 without `Retry-After` means the monthly request budget is spent or too many streams are open, so it is thrown at once. Downloads from the storage host are never retried.

Set the number of retries with `maxRetries` on the builder. Zero disables them:

```java
SynthientClient client = SynthientClient.builder()
        .maxRetries(4)
        .build();
```

## Timeouts

The client sets an idle timeout rather than an overall one, because streams and downloads are long-lived. It limits each wait for the response headers and then for each part of the body, 60 seconds by default:

```java
SynthientClient client = SynthientClient.builder()
        .timeout(Duration.ofSeconds(20))
        .build();
```

- When it runs out, the call throws `ApiTimeoutException`. Reading a download throws a plain `IOException` instead.
- Streams get a keepalive line about every 15 seconds, so keep the timeout above that.
- To cancel a call that is waiting for a response or a retry, interrupt its thread. To stop a stream or download that is already reading, close it from another thread.

## Error Handling

A request that fails with a non-success status throws an [`ApiException`](https://javadoc.io/doc/com.synthient/synthient-java/latest/com/synthient/ApiException.html):

| Method         | Type                        | Description                                    |
| -------------- | --------------------------- | ---------------------------------------------- |
| `statusCode()` | `int`                       | HTTP status code, such as `404`                |
| `type()`       | `String`                    | URI identifying the problem type               |
| `title()`      | `String`                    | Short summary of the problem type              |
| `detail()`     | `String`                    | Explanation of this occurrence                 |
| `errors()`     | `Map<String, List<String>>` | Validation messages by request field           |
| `requestId()`  | `String`                    | Request ID to give Synthient support           |
| `retryAfter()` | `Duration`                  | How long the server asked you to wait, or null |

`getMessage()` holds only the HTTP status, such as `403 Forbidden`, so the exception is safe to log. The string methods return an empty string when the response has no value.

### Example

```java
try {
    client.lookupIp("8.8.8.8");
} catch (ApiException e) {
    switch (e.statusCode()) {
        case 402 -> System.out.println("out of lookup credits");
        case 429 -> System.out.println(
                e.retryAfter() == null ? "monthly request budget spent" : "rate limited, retry after " + e.retryAfter());
        default -> System.out.println(
                "request " + e.requestId() + " failed: " + (e.detail().isEmpty() ? e.title() : e.detail()));
    }
}
```

### Error Types

Every exception the SDK throws is unchecked. Reading a download's `InputStream` can also throw `IOException`.

- [`SynthientException`](https://javadoc.io/doc/com.synthient/synthient-java/latest/com/synthient/SynthientException.html): the parent of the SDK's own exceptions. Thrown on its own, it means the API sent a response the SDK could not use.
  - [`ApiException`](https://javadoc.io/doc/com.synthient/synthient-java/latest/com/synthient/ApiException.html): the API, or the storage host during `download`, returned a non-success status.
  - [`ApiConnectionException`](https://javadoc.io/doc/com.synthient/synthient-java/latest/com/synthient/ApiConnectionException.html): no response arrived, because the connection failed or was lost, or the thread was interrupted.
    - [`ApiTimeoutException`](https://javadoc.io/doc/com.synthient/synthient-java/latest/com/synthient/ApiTimeoutException.html): connecting took too long, or the API was silent for longer than the timeout.
- `IllegalArgumentException`: an argument is invalid, such as a malformed address or export ID or a missing API key. No request is sent.
- `IllegalStateException`: the client was closed, or an `EventStream` was iterated twice.

<details><summary>Status codes</summary>

<br />

| Status | Meaning                                                                                           |
| ------ | ------------------------------------------------------------------------------------------------- |
| 400    | The request is invalid, such as an hourly export that was rolled up                               |
| 401    | The API key is malformed, unknown, or revoked                                                     |
| 402    | The team is out of lookup credits                                                                 |
| 403    | The key lacks the scope for the endpoint or feed                                                  |
| 404    | The export doesn't exist, or a daily export is still rolling up (`retryAfter()` is set)           |
| 422    | The input is invalid, such as a private address or a malformed domain                             |
| 429    | Rate limited if `retryAfter()` is set; otherwise the budget is spent or too many streams are open |
| 5xx    | The server failed                                                                                 |

</details>

## Server Selection

### Override Server URL Per-Client

The default server is `https://api.synthient.com/api/v4`. Use `baseUrl` on the builder to send requests elsewhere, such as a mock server in tests:

```java
SynthientClient client = SynthientClient.builder()
        .baseUrl("http://localhost:8080/api/v4")
        .build();
```

## Custom HTTP Client

Pass your own [OkHttp](https://square.github.io/okhttp/) `OkHttpClient` to `httpClient` on the builder, for example to use a proxy:

```java
OkHttpClient okHttp = new OkHttpClient.Builder()
        .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("proxy.example.com", 8080)))
        .build();

SynthientClient client = SynthientClient.builder()
        .httpClient(okHttp)
        .build();
```

- The SDK uses your connection pool, proxy, TLS settings, interceptors, and connect timeout, but sets its own read and write timeouts and no call timeout.
- It never lets OkHttp resend a request, so a billed lookup is never sent twice.
- Closing the SDK client leaves yours open.

`userAgent("my-app/1.2")` on the builder adds your product token to the `User-Agent` header.

## Debugging

`responseHook` on the builder calls a function after every API response, including retries, pages, and stream connections, with the status, request ID, rate limit, and headers:

```java
SynthientClient client = SynthientClient.builder()
        .responseHook(meta -> {
            System.err.println("status=" + meta.statusCode() + " request_id=" + meta.requestId());
            RateLimit rateLimit = meta.rateLimit();
            if (rateLimit != null) {
                System.err.println("rate limit: " + rateLimit.remaining() + " of " + rateLimit.limit() + " left");
            }
        })
        .build();
```

The hook must be safe for concurrent use, and an exception it throws fails the call.

> [!WARNING]
> Response headers can contain secrets, such as presigned download URLs. Avoid logging `meta.headers()` in production. `ResponseMetadata.toString()` leaves them out.

## gRPC Schema

The [`synthient-java-grpcschema`](https://javadoc.io/doc/com.synthient/synthient-java-grpcschema) artifact fetches the protobuf schema of the [Synthient gRPC API](https://docs.synthient.com/grpc) through server reflection:

```java
try {
    Schema schema = GrpcSchema.fetch(System.getenv().getOrDefault("SYNTHIENT_API_KEY", ""));
    for (FileDescriptorProto file : schema.descriptorSet().getFileList()) {
        System.out.println(file.getName() + " " + file.getPackage());
    }
} catch (SchemaException e) {
    String explanation = GrpcSchema.explain(e);
    System.err.println(explanation.isEmpty() ? e.toString() : explanation + " " + e);
}
```

`schema.descriptorSet()` is a protobuf `FileDescriptorSet`. `GrpcSchema.explain` describes common gRPC errors in plain language.

# Development

## Maturity

This SDK follows [Semantic Versioning](https://semver.org). Breaking changes ship only in a new major version, except that model records gain components in minor releases as the API adds fields, which can change their constructors and record patterns.

## Contributions

Bug reports and feature requests are welcome as [GitHub issues](https://github.com/synthient/synthient-java/issues). For questions about the API or your account, contact [Synthient support](https://docs.synthient.com/misc/support).

## License

[Apache 2.0](LICENSE)
