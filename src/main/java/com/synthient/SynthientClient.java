package com.synthient;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.synthient.model.Account;
import com.synthient.model.AnonymizerEvent;
import com.synthient.model.DomainInfo;
import com.synthient.model.HeliosAdbEvent;
import com.synthient.model.HeliosDnsEvent;
import com.synthient.model.HeliosHttpEvent;
import com.synthient.model.HeliosHttpsEvent;
import com.synthient.model.IpInfo;
import com.synthient.model.Ja4tEvent;
import com.synthient.model.ProxyEvent;
import com.synthient.model.TorrentEvent;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * A client for the Synthient API. It is immutable and safe for concurrent use; create one and reuse it.
 *
 * {@snippet :
 * SynthientClient client = new SynthientClient(); // reads SYNTHIENT_API_KEY
 * IpInfo info = client.lookupIp("8.8.8.8");
 * }
 *
 * <p>Failed calls throw {@link ApiException} when the API answers with an error status, {@link
 * ApiConnectionException} when no response arrives, and {@link SynthientException} when the response is
 * invalid. Requests are retried after rate limits that carry a {@code Retry-After}, and account and feed
 * requests also after server errors and after connection failures and timeouts before the response arrives,
 * except TLS failures. Neither the SDK nor its HTTP client ever resends a billed lookup after a failure the
 * server may have charged for.
 *
 * <p>Close the client to release its connections. A client built with a caller's {@link OkHttpClient} leaves
 * that client open.
 */
public final class SynthientClient implements AutoCloseable {
    /** The SDK version, sent in the {@code User-Agent} header. */
    public static final String VERSION = "1.0.0";

    /** The production API. */
    public static final String DEFAULT_BASE_URL = "https://api.synthient.com/api/v4";

    private static final int MAX_BATCH_SIZE = 1000;
    private static final Pattern IPV4 =
            Pattern.compile("(?:(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)");
    private static final Pattern IPV6_CHARACTERS = Pattern.compile("[0-9A-Fa-f:][0-9A-Fa-f:.]*");

    private final Transport transport;

    /**
     * Creates a client with the API key in the {@code SYNTHIENT_API_KEY} environment variable.
     *
     * @throws IllegalArgumentException if the variable is missing or blank, or the key is not printable ASCII
     */
    public SynthientClient() {
        this(builder());
    }

    /**
     * Creates a client with an API key.
     *
     * @throws IllegalArgumentException if the key is blank or not printable ASCII
     */
    public SynthientClient(String apiKey) {
        this(builder().apiKey(apiKey));
    }

    private SynthientClient(Builder builder) {
        String apiKey = builder.apiKey != null ? builder.apiKey : builder.environment.get("SYNTHIENT_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("missing API key: pass one or set SYNTHIENT_API_KEY");
        }
        apiKey = apiKey.strip();
        // OkHttp puts a rejected header value in its exception message, so the key is checked first.
        if (apiKey.chars().anyMatch(c -> c < 0x20 || c > 0x7e)) {
            throw new IllegalArgumentException("invalid API key: it must be printable ASCII");
        }
        String userAgent = "synthient-java/" + VERSION + (builder.userAgent == null ? "" : " " + builder.userAgent);
        transport = new Transport(
                builder.httpClient,
                builder.baseUrl,
                apiKey,
                userAgent,
                builder.maxRetries,
                builder.timeout,
                builder.responseHook,
                builder.sleeper);
    }

    /** Returns a builder for a client with custom options. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Looks up an IP address. Costs one credit; failed lookups are free. The request is never resent after
     * a connection failure, because the server may already have charged for it; only rate limits with a
     * {@code Retry-After} are retried.
     *
     * @param ip an IPv4 or IPv6 address literal
     * @throws IllegalArgumentException if {@code ip} is not an address literal; no request is sent
     */
    public IpInfo lookupIp(String ip) {
        if (!isIpLiteral(ip)) {
            throw new IllegalArgumentException("invalid IP address");
        }
        HttpUrl url = transport.url("lookup/ip").addPathSegment(ip).build();
        return transport.call("GET", url, null, Transport.Policy.RATE_LIMIT, IpInfo.class);
    }

    /**
     * Looks up up to 1000 IP addresses in one request, billed at 0.9 credits per distinct address, rounded
     * up.
     *
     * <p>Private and reserved addresses are skipped and duplicates are merged, so the results may be fewer
     * than the inputs and in a different order. Match them by {@link IpInfo#ip()}. An empty collection
     * returns an empty list without a request.
     *
     * @throws IllegalArgumentException if there are more than 1000 addresses or one is not an address
     *     literal; no request is sent
     */
    public List<IpInfo> lookupIps(Collection<String> ips) {
        if (ips.isEmpty()) {
            return List.of();
        }
        if (ips.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(ips.size() + " addresses exceed the batch limit of " + MAX_BATCH_SIZE);
        }
        int index = 0;
        for (String ip : ips) {
            if (ip == null || !isIpLiteral(ip)) {
                throw new IllegalArgumentException("invalid IP address at index " + index);
            }
            index++;
        }
        byte[] body = Json.MAPPER.writeValueAsBytes(Map.of("ips", List.copyOf(ips)));
        return transport
                .call(
                        "POST",
                        transport.url("lookup/ips").build(),
                        body,
                        Transport.Policy.RATE_LIMIT,
                        BatchResponse.class)
                .results();
    }

    /** Looks up honeypot intelligence for a domain, with the API's default of 50 recent events. Costs one credit. */
    public DomainInfo lookupDomain(String domain) {
        return fetchDomain(domain, null);
    }

    /**
     * Looks up honeypot intelligence for a domain. Costs one credit.
     *
     * @param domain the domain; internationalized names must be in Punycode
     * @param limit the number of recent events to return, clamped by the API to 1000
     * @throws IllegalArgumentException if the domain is empty, {@code .}, or {@code ..}, or the limit is less
     *     than 1; no request is sent
     */
    public DomainInfo lookupDomain(String domain, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1, got " + limit);
        }
        return fetchDomain(domain, limit);
    }

    private DomainInfo fetchDomain(String domain, @Nullable Integer limit) {
        if (domain.isEmpty() || domain.equals(".") || domain.equals("..")) {
            throw new IllegalArgumentException("invalid domain \"" + domain + "\"");
        }
        HttpUrl.Builder url = transport.url("lookup/domain").addPathSegment(domain);
        if (limit != null) {
            url.addQueryParameter("limit", limit.toString());
        }
        return transport.call("GET", url.build(), null, Transport.Policy.RATE_LIMIT, DomainInfo.class);
    }

    /** Returns the account that owns the API key, including its scopes and remaining lookup credits. */
    public Account account() {
        return transport.call(
                "GET", transport.url("account/me").build(), null, Transport.Policy.TRANSIENT, Account.class);
    }

    /**
     * Checks that the API is up.
     *
     * @throws ApiException with status 503 if the API is unhealthy
     */
    public void health() {
        transport.call("GET", transport.url("health").build(), null, Transport.Policy.RATE_LIMIT, JsonNode.class);
    }

    /** Returns the proxies feed. Streaming needs the {@code PROXY_FIREHOSE} scope and exports {@code PROXY_FEEDS}. */
    public Feed<ProxyEvent> proxies() {
        return new Feed<>(transport, "feeds/proxies", ProxyEvent.class);
    }

    /** Returns the anonymizers feed of VPN and anonymizing service address ranges. */
    public Feed<AnonymizerEvent> anonymizers() {
        return new Feed<>(transport, "feeds/anonymizers", AnonymizerEvent.class);
    }

    /** Returns the torrents feed. */
    public Feed<TorrentEvent> torrents() {
        return new Feed<>(transport, "feeds/torrents", TorrentEvent.class);
    }

    /** Returns the JA4T feed of TCP fingerprints seen from proxies. */
    public Feed<Ja4tEvent> ja4t() {
        return new Feed<>(transport, "feeds/ja4t", Ja4tEvent.class);
    }

    /** Returns the Helios feed of HTTP requests received by honeypots through proxies. */
    public Feed<HeliosHttpEvent> heliosHttp() {
        return new Feed<>(transport, "feeds/helio/http", HeliosHttpEvent.class);
    }

    /** Returns the Helios feed of TLS handshakes received by honeypots through proxies. */
    public Feed<HeliosHttpsEvent> heliosHttps() {
        return new Feed<>(transport, "feeds/helio/https", HeliosHttpsEvent.class);
    }

    /** Returns the Helios feed of DNS tunnels opened to honeypots through proxies. */
    public Feed<HeliosDnsEvent> heliosDns() {
        return new Feed<>(transport, "feeds/helio/dns", HeliosDnsEvent.class);
    }

    /** Returns the Helios feed of commands run on Android Debug Bridge honeypots. */
    public Feed<HeliosAdbEvent> heliosAdb() {
        return new Feed<>(transport, "feeds/helio/adb", HeliosAdbEvent.class);
    }

    /**
     * Closes the client. Calls waiting for a response are cancelled and later calls throw {@link
     * IllegalStateException}. Open streams and downloads keep running until they are closed.
     */
    @Override
    public void close() {
        transport.close();
    }

    /**
     * Returns whether a string is an IPv4 or IPv6 literal without ever resolving it: {@link
     * InetAddress#getByName} only skips DNS for strings that start with a hex digit or colon and contain a
     * colon, so anything else must be dotted-quad IPv4.
     */
    private static boolean isIpLiteral(String ip) {
        if (!ip.contains(":")) {
            return IPV4.matcher(ip).matches();
        }
        if (!IPV6_CHARACTERS.matcher(ip).matches()) {
            return false;
        }
        try {
            InetAddress.getByName(ip);
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    record BatchResponse(@JsonProperty("results") List<IpInfo> results) {
        BatchResponse {
            results = List.copyOf(requireNonNull(results, "results"));
        }
    }

    /** Builds a {@link SynthientClient}. A builder can be reused; each build creates an independent client. */
    public static final class Builder {
        private @Nullable String apiKey;
        private HttpUrl baseUrl = HttpUrl.get(DEFAULT_BASE_URL);
        private int maxRetries = 2;
        private Duration timeout = Duration.ofSeconds(60);
        private @Nullable OkHttpClient httpClient;
        private @Nullable String userAgent;
        private @Nullable Consumer<ResponseMetadata> responseHook;
        private Transport.Sleeper sleeper = Thread::sleep;
        private Map<String, String> environment = System.getenv();

        private Builder() {}

        /** Sets the API key. Without one, the client reads {@code SYNTHIENT_API_KEY}. */
        public Builder apiKey(String apiKey) {
            this.apiKey = requireNonNull(apiKey, "apiKey");
            return this;
        }

        /**
         * Sets the API base URL, by default {@value SynthientClient#DEFAULT_BASE_URL}.
         *
         * @throws IllegalArgumentException if the URL is not http or https, has no host, or has user info, a
         *     query, or a fragment
         */
        public Builder baseUrl(String baseUrl) {
            HttpUrl url = HttpUrl.parse(baseUrl);
            if (url == null
                    || !url.username().isEmpty()
                    || !url.password().isEmpty()
                    || url.query() != null
                    || url.fragment() != null) {
                throw new IllegalArgumentException("invalid base URL \"" + baseUrl + "\"");
            }
            this.baseUrl = url;
            return this;
        }

        /**
         * Sets how many times a failed request is retried, by default 2. Zero disables retries.
         *
         * @throws IllegalArgumentException if the count is negative
         */
        public Builder maxRetries(int maxRetries) {
            if (maxRetries < 0) {
                throw new IllegalArgumentException("max retries must not be negative, got " + maxRetries);
            }
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Sets the idle timeout, by default 60 seconds: the longest the client waits for the response headers,
         * and then for each part of the body. It bounds silence, not a whole call, so streams and downloads can
         * run for as long as data keeps arriving.
         *
         * @throws IllegalArgumentException if the timeout is not positive
         */
        public Builder timeout(Duration timeout) {
            if (timeout.isNegative() || timeout.isZero()) {
                throw new IllegalArgumentException("timeout must be positive, got " + timeout);
            }
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets the OkHttp client to derive the SDK's client from, for example to configure a proxy, TLS, or
         * interceptors. The SDK shares its connection pool and connect timeout but uses its own dispatcher and
         * read and write timeouts. It never follows the API's redirects, caches responses, or lets OkHttp resend
         * a request. Redirects within export storage are followed, but never from https to http. The SDK never
         * closes it.
         */
        public Builder httpClient(OkHttpClient httpClient) {
            this.httpClient = requireNonNull(httpClient, "httpClient");
            return this;
        }

        /** Adds a product token, such as {@code my-app/1.2}, after the SDK's {@code User-Agent}. */
        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent.isBlank() ? null : userAgent.strip();
            return this;
        }

        /**
         * Sets a function called with the metadata of every API response, including each retried attempt, each
         * page, and each stream connection. It runs on the calling thread and must be safe for concurrent use.
         * An exception it throws fails the call. Downloads from export storage are not reported.
         */
        public Builder responseHook(Consumer<ResponseMetadata> responseHook) {
            this.responseHook = requireNonNull(responseHook, "responseHook");
            return this;
        }

        Builder sleeper(Transport.Sleeper sleeper) {
            this.sleeper = sleeper;
            return this;
        }

        Builder environment(Map<String, String> environment) {
            this.environment = environment;
            return this;
        }

        /**
         * Creates the client.
         *
         * @throws IllegalArgumentException if no API key was set and {@code SYNTHIENT_API_KEY} is missing or
         *     blank, or the key is not printable ASCII
         */
        public SynthientClient build() {
            return new SynthientClient(this);
        }
    }
}
