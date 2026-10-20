package com.synthient;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.net.ssl.SSLException;
import okhttp3.Authenticator;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Dispatcher;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.jspecify.annotations.Nullable;

/**
 * Sends API requests, applies the retry policy, and reads bounded bodies.
 *
 * <p>Only this class decides whether to retry. OkHttp never resends a request on its own: connection
 * failures, redirects, and authentication challenges are returned rather than retried, and a 503's {@code
 * Retry-After}, which OkHttp would act on, is hidden from it until the response reaches this class.
 */
final class Transport {
    static final long MAX_HEADER_SECONDS = 1L << 31;

    private static final int MAX_RESPONSE_SIZE = 16 << 20;
    private static final int MAX_ERROR_SIZE = 64 << 10;
    private static final Duration MAX_RETRY_AFTER = Duration.ofMinutes(1);
    private static final Pattern INTEGER = Pattern.compile("-?\\d+");
    private static final MediaType JSON = MediaType.get("application/json");
    private static final String HIDDEN_RETRY_AFTER = "Synthient-Hidden-Retry-After";

    /** Which failures a request may be retried after. */
    enum Policy {
        /** Rate limits, 500/502/503/504, and connection failures. */
        TRANSIENT,
        /**
         * Only rate limits. Billed lookups use it so a failure the server may have charged for is never
         * repeated.
         */
        RATE_LIMIT
    }

    /** Lets tests replace the wait between attempts. */
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    /** The body is unread and can be closed from any thread. */
    record RawResponse(int statusCode, Headers headers, HttpUrl url, InputStream body) {}

    private final OkHttpClient http;
    private final OkHttpClient storage;
    private final boolean ownsConnections;
    private final HttpUrl baseUrl;
    private final String apiKey;
    private final String userAgent;
    private final int maxRetries;
    private final @Nullable Consumer<ResponseMetadata> responseHook;
    private final Sleeper sleeper;
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * The transport owns the dispatcher even when the client is borrowed. It runs calls on virtual threads
     * without OkHttp's per-host limit.
     */
    Transport(
            @Nullable OkHttpClient borrowed,
            HttpUrl baseUrl,
            String apiKey,
            String userAgent,
            int maxRetries,
            Duration timeout,
            @Nullable Consumer<ResponseMetadata> responseHook,
            Sleeper sleeper) {
        Dispatcher dispatcher = new Dispatcher(Executors.newVirtualThreadPerTaskExecutor());
        dispatcher.setMaxRequests(Integer.MAX_VALUE);
        dispatcher.setMaxRequestsPerHost(Integer.MAX_VALUE);
        OkHttpClient.Builder builder = borrowed == null
                ? new OkHttpClient.Builder().connectTimeout(Duration.ofSeconds(10))
                : borrowed.newBuilder();
        http = builder.dispatcher(dispatcher)
                .retryOnConnectionFailure(false)
                .followRedirects(false)
                .authenticator(Authenticator.NONE)
                .cache(null)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .callTimeout(Duration.ZERO)
                .addNetworkInterceptor(chain -> {
                    Response response = chain.proceed(chain.request());
                    String retryAfter = response.header("Retry-After");
                    return response.code() == 503 && retryAfter != null
                            ? response.newBuilder()
                                    .removeHeader("Retry-After")
                                    .header(HIDDEN_RETRY_AFTER, retryAfter)
                                    .build()
                            : response;
                })
                .build();
        // Export storage needs no API key, so its redirects can be followed, but never from https to http.
        storage = http.newBuilder()
                .followRedirects(true)
                .followSslRedirects(false)
                .build();
        ownsConnections = borrowed == null;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.userAgent = userAgent;
        this.maxRetries = maxRetries;
        this.responseHook = responseHook;
        this.sleeper = sleeper;
    }

    /** The path is split on slashes, so pass only fixed paths such as {@code lookup/ip}, never caller input. */
    HttpUrl.Builder url(String path) {
        return baseUrl.newBuilder().addPathSegments(path);
    }

    <T> T call(String method, HttpUrl url, byte @Nullable [] body, Policy policy, Class<T> type) {
        RawResponse response = send(method, url, body, "application/json", policy);
        if (response.statusCode() / 100 != 2) {
            throw apiError(response);
        }
        byte[] bytes;
        try (InputStream in = response.body()) {
            bytes = in.readNBytes(MAX_RESPONSE_SIZE + 1);
        } catch (IOException e) {
            throw connectionFailure(e);
        }
        if (bytes.length > MAX_RESPONSE_SIZE) {
            throw new SynthientException("response body exceeds " + MAX_RESPONSE_SIZE + " bytes");
        }
        return Json.decode(bytes, type);
    }

    /** Returns the last response, even one with an error status. The caller owns its body. */
    RawResponse send(String method, HttpUrl url, byte @Nullable [] body, String accept, Policy policy) {
        Request request = new Request.Builder()
                .url(url)
                .header("X-Api-Key", apiKey)
                .header("User-Agent", userAgent)
                .header("Accept", accept)
                .method(method, body == null ? null : RequestBody.create(body, JSON))
                .build();

        for (int attempt = 0; ; attempt++) {
            boolean canRetry = attempt < maxRetries;
            RawResponse response;
            try {
                response = exchange(http, request);
            } catch (ApiConnectionException e) {
                if (!canRetry || policy != Policy.TRANSIENT || !isTransient(e)) {
                    throw e;
                }
                sleep(backoff(attempt));
                continue;
            }
            notifyHook(response);

            Duration retryAfter = parseRetryAfter(response.headers().get("Retry-After"));
            int status = response.statusCode();
            boolean rateLimited = status == 429 && retryAfter != null;
            boolean serverError =
                    policy == Policy.TRANSIENT && (status == 500 || status == 502 || status == 503 || status == 504);
            if (!canRetry || !(rateLimited || serverError)) {
                return response;
            }
            if (retryAfter != null && retryAfter.compareTo(MAX_RETRY_AFTER) > 0) {
                return response;
            }
            closeQuietly(response.body());
            Duration delay = backoff(attempt);
            sleep(retryAfter != null && retryAfter.compareTo(delay) > 0 ? retryAfter : delay);
        }
    }

    RawResponse fetchStorage(HttpUrl url) {
        return exchange(
                storage,
                new Request.Builder().url(url).header("User-Agent", userAgent).build());
    }

    /** Interrupting the calling thread while it waits for the response headers cancels the call. */
    private RawResponse exchange(OkHttpClient client, Request request) {
        if (closed.get()) {
            throw new IllegalStateException("the client is closed");
        }
        Call call = client.newCall(request);
        CompletableFuture<Response> future = new CompletableFuture<>();
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call failed, IOException e) {
                future.completeExceptionally(e);
            }

            @Override
            public void onResponse(Call succeeded, Response response) {
                if (!future.complete(response)) {
                    response.close();
                }
            }
        });
        Response response;
        try {
            response = future.get();
        } catch (InterruptedException e) {
            if (!future.cancel(false) && future.state() == Future.State.SUCCESS) {
                future.resultNow().close();
            }
            call.cancel();
            Thread.currentThread().interrupt();
            throw new ApiConnectionException("interrupted while waiting for the response", e);
        } catch (ExecutionException e) {
            throw connectionFailure(e.getCause() == null ? e : e.getCause());
        }
        Headers headers = response.headers();
        String retryAfter = headers.get(HIDDEN_RETRY_AFTER);
        if (retryAfter != null) {
            headers = headers.newBuilder()
                    .removeAll(HIDDEN_RETRY_AFTER)
                    .set("Retry-After", retryAfter)
                    .build();
        }
        return new RawResponse(
                response.code(),
                headers,
                response.request().url(),
                new CallBody(call, response.body().byteStream()));
    }

    /**
     * A response body that can be closed from any thread. Closing cancels the call, which fails a blocked
     * read at once, and then closes the body once no read is in progress, because OkHttp's streams are not
     * safe for concurrent use.
     */
    private static final class CallBody extends InputStream {
        private final Call call;
        private final InputStream body;
        private final Lock lock = new ReentrantLock();

        CallBody(Call call, InputStream body) {
            this.call = call;
            this.body = body;
        }

        @Override
        public int read() throws IOException {
            lock.lock();
            try {
                return body.read();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            lock.lock();
            try {
                return body.read(bytes, offset, length);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void close() throws IOException {
            call.cancel();
            lock.lock();
            try {
                body.close();
            } finally {
                lock.unlock();
            }
        }
    }

    private void notifyHook(RawResponse response) {
        if (responseHook == null) {
            return;
        }
        try {
            responseHook.accept(ResponseMetadata.of(response.statusCode(), response.headers()));
        } catch (RuntimeException | Error e) {
            closeQuietly(response.body());
            throw e;
        }
    }

    private void sleep(Duration delay) {
        try {
            sleeper.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiConnectionException("interrupted while waiting to retry", e);
        }
    }

    ApiException apiError(RawResponse response) {
        byte[] body = new byte[0];
        IOException failure = null;
        try (InputStream in = response.body()) {
            body = in.readNBytes(MAX_ERROR_SIZE);
        } catch (IOException e) {
            failure = e;
        }
        return ApiException.fromResponse(response.statusCode(), response.headers(), body, failure);
    }

    /** Stops new requests and cancels calls waiting for a response. */
    void close() {
        if (closed.compareAndSet(false, true)) {
            http.dispatcher().cancelAll();
            http.dispatcher().executorService().shutdown();
            if (ownsConnections) {
                http.connectionPool().evictAll();
            }
        }
    }

    static ApiConnectionException connectionFailure(Throwable cause) {
        if (Thread.currentThread().isInterrupted()) {
            return new ApiConnectionException("interrupted while reading the response", cause);
        }
        if (cause instanceof SocketTimeoutException) {
            return new ApiTimeoutException("timed out waiting for the response", cause);
        }
        return new ApiConnectionException(
                "connection failed: " + cause.getClass().getSimpleName(), cause);
    }

    private static boolean isTransient(ApiConnectionException e) {
        if (Thread.currentThread().isInterrupted()) {
            return false;
        }
        for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof SSLException) {
                return false;
            }
        }
        return true;
    }

    static Duration backoff(int attempt) {
        long seconds = Math.min(1L << Math.min(attempt, 5), 30);
        return Duration.ofNanos(
                (long) (seconds * 1e9 * ThreadLocalRandom.current().nextDouble(0.75, 1.25)));
    }

    /**
     * Parses a Retry-After value in seconds or as an HTTP date. Seconds are clamped to [0, 2^31] and a past
     * date is zero. Returns null for a missing or malformed value.
     */
    static @Nullable Duration parseRetryAfter(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        if (INTEGER.matcher(trimmed).matches()) {
            long seconds;
            try {
                seconds = Long.parseLong(trimmed);
            } catch (NumberFormatException e) {
                seconds = trimmed.startsWith("-") ? 0 : MAX_HEADER_SECONDS;
            }
            return Duration.ofSeconds(Math.clamp(seconds, 0, MAX_HEADER_SECONDS));
        }
        try {
            Instant date = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant();
            Duration until = Duration.between(Instant.now(), date);
            return until.isNegative() ? Duration.ZERO : until;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    static void closeQuietly(InputStream body) {
        try {
            body.close();
        } catch (IOException ignored) {
            // Closing only releases the connection; there is nothing to recover.
        }
    }
}
