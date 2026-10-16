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

Add `com.synthient:synthient-java` to your build. The [gRPC schema fetcher](#grpc-schema) is a separate artifact, `com.synthient:synthient-java-grpcschema`, so add it only if you need it, with the same version.

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

Java 21 or later. The SDK depends on OkHttp 5 and Jackson 3, plus the JSpecify annotations. The `synthient-java-grpcschema` artifact also depends on gRPC and protobuf, which your application pulls in only if it adds that artifact.

## API Keys

Every request to the HTTP API needs a Synthient API key. Keys are issued from the Synthient dashboard; if you don't have one yet, [contact us](mailto:contact@synthient.com). Each key carries scopes that decide what it can call. `BASIC` covers the lookups and `account()`, and each feed has one scope for its real-time stream and one for its exports, listed under [Feeds](#feeds). See [Authentication](https://docs.synthient.com/authentication) for how scopes work.

If a request fails even though you are sending a key, it is usually one of these:

- **401 Unauthorized**: the key is malformed, unknown, or revoked. Check that it was loaded from the environment and isn't truncated.
- **402 Payment Required**: the team is out of lookup credits. `account()` reports the remaining balance and when it resets.
- **403 Forbidden**: the key lacks the scope for that endpoint or feed. `account()` lists the key's scopes.

## Authentication

This SDK supports the following security scheme:

| Name     | Type    | Scheme             | Environment Variable |
| -------- | ------- | ------------------ | -------------------- |
| `apiKey` | API key | `x-api-key` header | `SYNTHIENT_API_KEY`  |

`new SynthientClient()` reads the key from `SYNTHIENT_API_KEY`. To pass it yourself, for example from a secret manager, use the constructor that takes a key or `apiKey` on the builder:

```java
SynthientClient client = new SynthientClient(apiKey);
```

Creating a client throws `IllegalArgumentException` if the key is blank or not printable ASCII, or if no key is passed and `SYNTHIENT_API_KEY` is unset. A null key throws `NullPointerException`. Creating a client makes no network calls, so an invalid key shows up as a 401 on the first request. The key is sent only to the Synthient API, never to the storage host that serves export downloads.

## SDK Example Usage

### Look up an IP address

Get the network, location, and risk intelligence for an IPv4 or IPv6 address. Each lookup costs one credit, and failed lookups are free.

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

`network()`, `location()`, and `intelligence()` return null when the API has no data for that section, so check them before reading their fields. A client is safe for concurrent use and holds a connection pool, so create one, reuse it across your application, and close it on shutdown. The examples below reuse `client`. Runnable programs are in [examples](examples/src/main/java/com/synthient/examples), for example `./gradlew -q :examples:lookupIp --args=8.8.8.8`.

### Look up many IP addresses

Look up as many as 1000 addresses in one request, billed at `ceil(n * 0.9)` credits for the `n` distinct addresses looked up. Private and reserved addresses are skipped and duplicates collapse into one result, so match results by `ip()` rather than by index.

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

Get the honeypot traffic observed for a domain, with its most recent events. A domain that has never been observed returns an inactive status and zero counts rather than an error. Each lookup costs one credit.

```java
DomainInfo domain = client.lookupDomain("example.com", 10);
System.out.println(domain.status() + " " + domain.stats().events24h());
for (DomainEvent event : domain.recentEvents()) {
    System.out.println(event.timestamp() + " " + event.targetSubdomain() + " " + event.port());
}
```

### Check your account

Get the account that owns the API key, with the key's scopes and the team's remaining lookup credits. It costs no credits.

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
- [`account`](https://javadoc.io/doc/com.synthient/synthient-java/latest/com/synthient/SynthientClient.html#account()) - Get the account that owns the API key, the key's scopes, and the team's remaining lookup credits
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

`stream()` connects to a feed's real-time events and returns an `EventStream`, which you iterate over and close to disconnect. The server keeps a stream open for up to 30 minutes and then closes it, which ends the loop without an exception. The API has no resume cursor, so connect again to continue:

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
        // Pause after a failure so a failing stream doesn't reconnect in a tight loop.
        Thread.sleep(2000);
    }
}
```

The loop stops on a 401 or 403, because reconnecting can't fix a rejected key or a missing scope. After any other failure it waits 2 seconds before reconnecting, and after the server's routine close it reconnects at once. When the API refuses a connection, `stream()` throws an `ApiException`, and a 429 without `retryAfter()` means the key already has too many open streams. Once connected, a lost connection throws `ApiConnectionException` and an event that can't be decoded throws `SynthientException`, and either ends the stream. Closing the stream from another thread ends a blocked read.

## Pagination

`listExports(limit, cursor)` returns one page of a feed's exports, newest first. `listExports()` returns the first page at the API's default size of 100:

```java
ExportPage page = client.proxies().listExports(50, null);
for (Export export : page.exports()) {
    System.out.println(export.id() + " " + export.kind() + " " + export.rowCount());
}
```

To get the next page, pass `page.nextCursor()` as the cursor. It is null on the last page. `allExports()` does the paging for you. It fetches pages as the loop needs them and stops fetching when the loop breaks:

```java
for (Export export : client.proxies().allExports()) {
    System.out.println(export.id() + " " + export.kind() + " " + export.rowCount());
}
```

A page that fails to load throws from the loop. Each new loop over the same `Iterable` starts again from the first page.

On the first page, the newest hourly export has the ID `latest`, a moving alias for the freshest export. To keep a stable ID, build it from the export's `date()` and `hour()`, such as `2026-10-14/21`.

## Downloads

Exports are Parquet files. Identify one by the `id()` of an `Export` from a listing, by `ExportId.LATEST` for the freshest one, or by `ExportId.daily(date)` or `ExportId.hourly(date, hour)` for a UTC day or hour. `download` returns the file's contents as an `InputStream`, which you must close. Reading it throws an `IOException`, not an SDK exception, if the transfer stalls or drops, and the transfer is not retried:

```java
try (InputStream body = client.proxies().download(ExportId.LATEST)) {
    Files.copy(body, Path.of("proxies.parquet"), StandardCopyOption.REPLACE_EXISTING);
}
```

The file comes from a presigned storage URL that is requested without your API key. `exportMeta` returns the SHA-256 checksum to verify it against. Use the same dated ID for both calls, such as `ExportId.hourly(date, hour)`, because `latest` can move to a newer export in between. `downloadUrl` returns the presigned URL itself for tools that read Parquet over HTTPS. The URL grants access without a key for 24 hours, so treat it as a secret.

An hourly export is available until its day's daily export is rolled up, shortly after 00:30 UTC the next day. After that, requests for the hour return a 400. While a daily export is being rolled up, requests for it return a 404 with `retryAfter()` set.

## Retries

The client retries a failed request up to twice by default, backing off exponentially with jitter and honoring `Retry-After`:

- Account and feed requests are retried on 500, 502, 503, and 504 responses, on 429 responses that carry `Retry-After`, and on connection failures and timeouts that happen before the response arrives. TLS errors are not retried, and neither is a failure while reading a response body, stream, or download, or a download's transfer from storage.
- Billed lookups (`lookupIp`, `lookupIps`, and `lookupDomain`) are retried only on 429 responses that carry `Retry-After`, which the rate limiter returns before doing any work, so a lookup is never charged twice. OkHttp is configured so it never resends one on its own either. `health()` is retried the same way, so an unhealthy API is reported at once.

A 429 without `Retry-After` means the key's monthly request budget is spent or it has too many open streams, so it is thrown without a retry. A `Retry-After` longer than a minute is thrown to you instead of waited out. A stream's connection is retried like any feed request, but events are never replayed once it is open.

To change the number of retries, set `maxRetries` on the builder. Zero disables retries:

```java
SynthientClient client = SynthientClient.builder()
        .maxRetries(4)
        .build();
```

## Timeouts

The client sets an idle timeout rather than an overall one, because streams and downloads are long-lived. It bounds how long the client waits for the response headers, and then for each part of the body, so a stream or download runs for as long as data keeps arriving. The default is 60 seconds:

```java
SynthientClient client = SynthientClient.builder()
        .timeout(Duration.ofSeconds(20))
        .build();
```

When it runs out, the call throws `ApiTimeoutException`, after any retries for account and feed requests. Reading a download's `InputStream` throws a plain `IOException` instead, such as `java.net.SocketTimeoutException`. The server sends a heartbeat line on every stream every 15 seconds, so keep the timeout above that if you use streams. Connecting has its own timeout of 10 seconds, or your `OkHttpClient`'s if you pass one, and running out of it also throws `ApiTimeoutException`.

To stop a call that is waiting for its response or for a retry, interrupt its thread. The call throws `ApiConnectionException` and the thread's interrupt flag stays set. A stream or download that is already reading may not notice an interrupt until more data arrives, so close it from another thread instead.

## Error Handling

A request that fails with a non-success HTTP status throws an [`ApiException`](https://javadoc.io/doc/com.synthient/synthient-java/latest/com/synthient/ApiException.html). It has the following methods:

| Method         | Type                        | Description                                                                                 |
| -------------- | --------------------------- | ------------------------------------------------------------------------------------------- |
| `statusCode()` | `int`                       | HTTP response status code, such as `404`                                                    |
| `type()`       | `String`                    | URI identifying the problem type, such as `https://docs.synthient.com/errors/validation-failed` |
| `title()`      | `String`                    | Short summary of the problem type                                                           |
| `detail()`     | `String`                    | Explanation of this occurrence. For a plain-text error, the response body                   |
| `errors()`     | `Map<String, List<String>>` | Validation messages by request field                                                        |
| `requestId()`  | `String`                    | Request ID to give Synthient support                                                        |
| `retryAfter()` | `Duration`                  | How long the server asked you to wait before retrying, or null                              |

The string methods return an empty string when the response has no value. `getMessage()` holds only the HTTP status, such as `403 Forbidden`, so the exception is safe to log. The server's explanation is in `detail()`.

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

- [`SynthientException`](https://javadoc.io/doc/com.synthient/synthient-java/latest/com/synthient/SynthientException.html): the parent of every exception the SDK throws for a failed call. Thrown directly, it means the API sent a response the SDK could not use, such as invalid JSON.
  - [`ApiException`](https://javadoc.io/doc/com.synthient/synthient-java/latest/com/synthient/ApiException.html): the API returned a non-success status.
  - [`ApiConnectionException`](https://javadoc.io/doc/com.synthient/synthient-java/latest/com/synthient/ApiConnectionException.html): no response arrived, because the connection failed or was lost, or the calling thread was interrupted.
    - [`ApiTimeoutException`](https://javadoc.io/doc/com.synthient/synthient-java/latest/com/synthient/ApiTimeoutException.html): connecting took longer than the connect timeout, or the API sent nothing for longer than the client's timeout.
- `IllegalArgumentException`: an argument was invalid, such as an address that is not an IP literal, an invalid export ID, or a missing API key. No request is sent.
- `IllegalStateException`: the client was closed, or an `EventStream` was iterated a second time.

<details><summary>Status codes</summary>

<br />

| Status | Meaning                                                                                                            |
| ------ | ------------------------------------------------------------------------------------------------------------------ |
| 400    | The request is invalid, such as asking for an hourly export that has been rolled up                                |
| 401    | The API key is malformed, unknown, or revoked                                                                      |
| 402    | The team is out of lookup credits                                                                                  |
| 403    | The key lacks the scope for the endpoint or feed                                                                   |
| 404    | The export does not exist, or a daily export is still being rolled up and `retryAfter()` is set                    |
| 422    | The input is invalid, such as a private or reserved address, a malformed domain, or a batch with no usable address |
| 429    | With `retryAfter()`, the rate limit was hit. Without it, the request budget is spent or too many streams are open  |
| 5xx    | The server failed. Account and feed requests are retried on 500, 502, 503, and 504                                 |

</details>

## Server Selection

### Override Server URL Per-Client

The default server is `https://api.synthient.com/api/v4`. To send requests elsewhere, such as through a gateway or to a mock server in tests, set `baseUrl` on the builder. The URL includes the version path:

```java
SynthientClient client = SynthientClient.builder()
        .baseUrl("http://localhost:8080/api/v4")
        .build();
```

## Custom HTTP Client

The client sends requests with [OkHttp](https://square.github.io/okhttp/). Pass your own `OkHttpClient` to `httpClient` on the builder, for example to route requests through a proxy:

```java
OkHttpClient okHttp = new OkHttpClient.Builder()
        .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("proxy.example.com", 8080)))
        .build();

SynthientClient client = SynthientClient.builder()
        .httpClient(okHttp)
        .build();
```

The SDK derives its own client from yours. It shares your connection pool, proxy, TLS settings, interceptors, and connect timeout, but uses its own dispatcher and read and write timeouts, and no call timeout. It never caches responses or follows the API's redirects, so your API key never reaches the export storage host, and it never lets OkHttp resend a request, so a billed lookup is never sent twice. Redirects within export storage are followed, but never from https to http. Closing the SDK client leaves yours open.

To identify your application, `userAgent("my-app/1.2")` on the builder appends a product token to the `User-Agent` header.

## Debugging

`responseHook` on the builder sets a function that is called after every API response arrives, including each retry, page, and stream connection. It receives a `ResponseMetadata` with the status code, the request ID to give Synthient support, the operation's rate limit, and the response headers:

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

The hook runs on the calling thread and must be safe for concurrent use. An exception it throws fails the call. Storage download responses are not passed to it.

> [!WARNING]
> Response headers can contain secrets, such as the presigned URL in an export download's `Location` header. Avoid logging `meta.headers()` in production. `ResponseMetadata.toString()` leaves the headers out.

## gRPC Schema

The [`synthient-java-grpcschema`](https://javadoc.io/doc/com.synthient/synthient-java-grpcschema) artifact fetches the protobuf schema of the [Synthient gRPC API](https://docs.synthient.com/grpc) through gRPC server reflection:

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

`schema.descriptorSet()` is a protobuf `FileDescriptorSet` with each file after the files it imports. `GrpcSchema.explain` describes common gRPC errors in plain language, and returns an empty string for the rest. To fetch from another endpoint, resolve specific symbols, or change the 30-second deadline, pass `FetchOptions` to `GrpcSchema.fetch`.

# Development

## Maturity

This SDK follows [Semantic Versioning](https://semver.org). Breaking changes to its public API ship only in a new major version. The one exception is the model records, which gain components in minor releases as the API adds fields, so their constructors and record patterns can change. `synthient-java` and `synthient-java-grpcschema` are released together with the same version.

## Contributions

Bug reports and feature requests are welcome as [GitHub issues](https://github.com/synthient/synthient-java/issues). For questions about the API or your account, contact [Synthient support](https://docs.synthient.com/misc/support).

## License

[Apache 2.0](LICENSE)
