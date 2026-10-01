package com.synthient;

import com.synthient.model.Export;
import com.synthient.model.ExportMeta;
import com.synthient.model.ExportPage;
import java.io.InputStream;
import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import okhttp3.HttpUrl;
import org.jspecify.annotations.Nullable;

/**
 * A Synthient data feed: a real-time {@link #stream() stream} and hourly and daily Parquet exports. Get one
 * from the client, such as {@link SynthientClient#proxies()}. Each feed needs its own scopes on the API
 * key.
 *
 * @param <T> the type of the feed's stream events
 */
public final class Feed<T> {
    private final Transport transport;
    private final String path;
    private final Class<T> eventType;

    Feed(Transport transport, String path, Class<T> eventType) {
        this.transport = transport;
        this.path = path;
        this.eventType = eventType;
    }

    /** Returns the first page of exports, newest first. */
    public ExportPage listExports() {
        return listExports(null, null);
    }

    /** Returns one page of exports, newest first. */
    public ExportPage listExports(ListExportsParams params) {
        return listExports(params.limit(), params.cursor());
    }

    private ExportPage listExports(@Nullable Integer limit, @Nullable String cursor) {
        HttpUrl.Builder url = transport.url(path + "/export");
        if (cursor != null) {
            url.addQueryParameter("cursor", cursor);
        }
        if (limit != null) {
            url.addQueryParameter("limit", limit.toString());
        }
        return transport.call("GET", url.build(), null, Transport.Policy.TRANSIENT, ExportPage.class);
    }

    /** Returns every export, newest first. See {@link #allExports(ListExportsParams)}. */
    public Iterable<Export> allExports() {
        return allExports(ListExportsParams.builder().build());
    }

    /**
     * Returns every export from the given cursor on, newest first, fetching pages as iteration reaches
     * them. Each call to {@code iterator()} starts again from the first page, and stopping early fetches
     * nothing more.
     *
     * <p>A failed page throws from {@code hasNext()} and can be retried by calling it again. A cursor the
     * API already returned throws {@link SynthientException} rather than looping.
     */
    public Iterable<Export> allExports(ListExportsParams params) {
        return () -> new Iterator<>() {
            private final Set<String> seen = new HashSet<>();
            private @Nullable String cursor = params.cursor();
            private Iterator<Export> page = Collections.emptyIterator();
            private boolean lastPage;

            @Override
            public boolean hasNext() {
                while (!page.hasNext()) {
                    if (lastPage) {
                        return false;
                    }
                    if (cursor != null && seen.contains(cursor)) {
                        throw new SynthientException("export listing returned a repeated cursor");
                    }
                    ExportPage result = listExports(params.limit(), cursor);
                    if (cursor != null) {
                        seen.add(cursor);
                    }
                    page = result.exports().iterator();
                    cursor = result.nextCursor();
                    lastPage = cursor == null;
                }
                return true;
            }

            @Override
            public Export next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return page.next();
            }
        };
    }

    /** Returns the metadata of an export, including its checksum and Parquet columns. */
    public ExportMeta exportMeta(ExportId id) {
        HttpUrl url = transport
                .url(path + "/export")
                .addPathSegments(id.toPath())
                .addPathSegment("meta")
                .build();
        return transport.call("GET", url, null, Transport.Policy.TRANSIENT, ExportMeta.class);
    }

    /**
     * Returns the presigned URL of an export's Parquet file. It is valid for 24 hours and grants access to
     * the file without an API key, so treat it as a secret.
     */
    public URI downloadUrl(ExportId id) {
        return exportLocation(id).uri();
    }

    /**
     * Downloads an export's Parquet file. The API key is not sent to the storage host. The caller must close
     * the returned stream. Compare the file's SHA-256 with {@link Export#checksum()} to check it.
     *
     * <p>The download is not retried, and the idle timeout applies while reading.
     */
    public InputStream download(ExportId id) {
        Transport.RawResponse response = transport.fetchStorage(exportLocation(id));
        if (response.statusCode() == 200) {
            return response.body();
        }
        Transport.closeQuietly(response.body());
        throw ApiException.fromResponse(response.statusCode(), response.headers(), new byte[0], null);
    }

    /**
     * Asks the API for an export and returns the storage location it redirects to. The API's redirect is
     * followed by hand so that the API key is never sent to the storage host. The location is never
     * included in errors because it is a presigned URL.
     */
    private HttpUrl exportLocation(ExportId id) {
        HttpUrl url =
                transport.url(path + "/export").addPathSegments(id.toPath()).build();
        Transport.RawResponse response =
                transport.send("GET", url, null, "application/json", Transport.Policy.TRANSIENT);
        int status = response.statusCode();
        if (status >= 400) {
            throw transport.apiError(response);
        }
        Transport.closeQuietly(response.body());
        if (status < 300) {
            throw new SynthientException("expected a redirect to the export, got status " + status);
        }
        String location = response.headers().get("Location");
        HttpUrl target =
                location == null || location.isBlank() ? null : response.url().resolve(location);
        if (target == null) {
            throw new SynthientException("export redirect has no valid location");
        }
        checkRedirect(response.url(), target);
        return target;
    }

    /** Rejects a redirect that carries user info or downgrades from https to http. */
    static void checkRedirect(HttpUrl from, HttpUrl target) {
        if (!target.username().isEmpty() || !target.password().isEmpty()) {
            throw new SynthientException("export redirect has an unsupported location");
        }
        if (from.isHttps() && !target.isHttps()) {
            throw new SynthientException("export redirect does not use https");
        }
    }

    /**
     * Connects to the feed's real-time stream. The connection is retried like other requests, but events
     * are never replayed once the stream is open.
     *
     * @throws ApiException if the API refuses the connection. A 429 without {@link ApiException#retryAfter()}
     *     means the key already has too many open streams.
     */
    public EventStream<T> stream() {
        HttpUrl url = transport.url(path + "/stream").build();
        Transport.RawResponse response =
                transport.send("GET", url, null, "application/x-ndjson", Transport.Policy.TRANSIENT);
        if (response.statusCode() != 200) {
            throw transport.apiError(response);
        }
        return new EventStream<>(response.body(), eventType);
    }
}
