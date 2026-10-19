package com.synthient;

import com.synthient.model.Export;
import com.synthient.model.ExportMeta;
import com.synthient.model.ExportPage;
import java.io.InputStream;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private static final Pattern EXPORT_ID = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})(?:/(\\d{1,2}))?");

    private final Transport transport;
    private final String path;
    private final Class<T> eventType;

    Feed(Transport transport, String path, Class<T> eventType) {
        this.transport = transport;
        this.path = path;
        this.eventType = eventType;
    }

    /** Returns the first page of exports, newest first, with the API's default page size of 100. */
    public ExportPage listExports() {
        return fetchPage(null, null);
    }

    /**
     * Returns one page of exports, newest first.
     *
     * @param limit the page size. The API allows up to 500 and clamps larger values.
     * @param cursor the cursor to start from, taken from {@link ExportPage#nextCursor()}, or null for the
     *     first page
     * @throws IllegalArgumentException if the limit is less than 1
     */
    public ExportPage listExports(int limit, @Nullable String cursor) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1, got " + limit);
        }
        return fetchPage(limit, cursor);
    }

    private ExportPage fetchPage(@Nullable Integer limit, @Nullable String cursor) {
        HttpUrl.Builder url = transport.url(path + "/export");
        if (cursor != null) {
            url.addQueryParameter("cursor", cursor);
        }
        if (limit != null) {
            url.addQueryParameter("limit", limit.toString());
        }
        return transport.call("GET", url.build(), null, Transport.Policy.TRANSIENT, ExportPage.class);
    }

    /** Returns every export, newest first. See {@link #allExports(int, String)}. */
    public Iterable<Export> allExports() {
        return exports(null, null);
    }

    /**
     * Returns every export from the given cursor on, newest first, fetching pages as iteration reaches
     * them. Each call to {@code iterator()} starts again from that cursor, and stopping early fetches
     * nothing more.
     *
     * <p>A failed page throws from {@code hasNext()} and can be retried by calling it again. A cursor the
     * API already returned throws {@link SynthientException} rather than looping.
     *
     * @param limit the page size. The API allows up to 500 and clamps larger values.
     * @param cursor the cursor to start from, taken from {@link ExportPage#nextCursor()}, or null for the
     *     first page
     * @throws IllegalArgumentException if the limit is less than 1
     */
    public Iterable<Export> allExports(int limit, @Nullable String cursor) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1, got " + limit);
        }
        return exports(limit, cursor);
    }

    private Iterable<Export> exports(@Nullable Integer limit, @Nullable String start) {
        return () -> new Iterator<>() {
            private final Set<String> seen = new HashSet<>();
            private @Nullable String cursor = start;
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
                    ExportPage result = fetchPage(limit, cursor);
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

    /**
     * Returns the metadata of an export, including its checksum and Parquet columns.
     *
     * @param id an export ID from {@link Export#id()} or {@link ExportId}
     * @throws IllegalArgumentException if the ID is invalid; no request is sent
     */
    public ExportMeta exportMeta(String id) {
        HttpUrl url = transport
                .url(path + "/export")
                .addPathSegments(exportPath(id))
                .addPathSegment("meta")
                .build();
        return transport.call("GET", url, null, Transport.Policy.TRANSIENT, ExportMeta.class);
    }

    /**
     * Returns the presigned URL of an export's Parquet file. It is valid for 24 hours and grants access to
     * the file without an API key, so treat it as a secret.
     *
     * @param id an export ID from {@link Export#id()} or {@link ExportId}
     * @throws IllegalArgumentException if the ID is invalid; no request is sent
     */
    public URI downloadUrl(String id) {
        return exportLocation(id).uri();
    }

    /**
     * Downloads an export's Parquet file. The API key is not sent to the storage host. The caller must close
     * the returned stream. Compare the file's SHA-256 with {@link Export#checksum()} to check it.
     *
     * <p>The download is not retried, and the idle timeout applies while reading.
     *
     * @param id an export ID from {@link Export#id()} or {@link ExportId}
     * @throws IllegalArgumentException if the ID is invalid; no request is sent
     */
    public InputStream download(String id) {
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
    private HttpUrl exportLocation(String id) {
        HttpUrl url =
                transport.url(path + "/export").addPathSegments(exportPath(id)).build();
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

    /**
     * Returns the path segments of an export ID, with the hour unpadded.
     *
     * @throws IllegalArgumentException if the ID is invalid
     */
    static String exportPath(String id) {
        if (id.equals(ExportId.LATEST)) {
            return id;
        }
        Matcher matcher = EXPORT_ID.matcher(id);
        if (matcher.matches()) {
            try {
                LocalDate date = LocalDate.parse(matcher.group(1));
                String hour = matcher.group(2);
                if (hour == null) {
                    return date.toString();
                }
                int parsed = Integer.parseInt(hour);
                if (parsed <= 23) {
                    return date + "/" + parsed;
                }
            } catch (DateTimeParseException e) {
                // A day that does not exist, such as 2026-02-30, is invalid too.
            }
        }
        throw new IllegalArgumentException("invalid export ID \"" + id + "\"");
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
     * @throws ApiException if the API refuses the connection
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
