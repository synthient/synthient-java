package com.synthient;

import com.synthient.model.Export;
import com.synthient.model.ExportMeta;
import com.synthient.model.ExportPage;
import com.synthient.model.ExportVariant;
import com.synthient.model.ExportVolume;
import java.io.InputStream;
import java.net.URI;
import okhttp3.HttpUrl;
import org.jspecify.annotations.Nullable;

/**
 * A Synthient data feed: a real-time {@link #stream() stream} and hourly and daily Parquet exports. Get one
 * from the client, such as {@link SynthientClient#proxies()}. Each feed needs its own scopes on the API
 * key.
 *
 * <p>Only the proxies, anonymizers, and JA4T feeds publish {@link ExportVariant#LITE lite} exports. Asking another
 * feed for them lists nothing and finds no export.
 *
 * @param <T> the type of the feed's stream events
 */
public final class Feed<T> {
    private final Transport transport;
    private final String path;
    private final Class<T> eventType;
    private final Exports exports;

    Feed(Transport transport, String path, Class<T> eventType) {
        this.transport = transport;
        this.path = path;
        this.eventType = eventType;
        this.exports = new Exports(transport, path, true);
    }

    /** Returns the first page of exports, newest first, with the API's default page size of 100. */
    public ExportPage listExports() {
        return exports.page(null, null, null, null);
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
        return exports.page(limit, cursor, null, null);
    }

    /**
     * Returns one page of exports, newest first.
     *
     * @param limit the page size. The API allows up to 500 and clamps larger values.
     * @param cursor the cursor to start from, taken from {@link ExportPage#nextCursor()}, or null for the
     *     first page
     * @param day a UTC day such as {@code 2026-05-03}, from {@link ExportId#daily}, to list only its exports, or
     *     null for every day
     * @param variant {@link ExportVariant#LITE} to list lite exports, or null for full ones
     * @throws IllegalArgumentException if the limit is less than 1
     */
    public ExportPage listExports(int limit, @Nullable String cursor, @Nullable String day, @Nullable String variant) {
        return exports.page(limit, cursor, day, variant);
    }

    /** Returns every export, newest first. See {@link #allExports(int, String, String, String)}. */
    public Iterable<Export> allExports() {
        return exports.all(null, null, null, null);
    }

    /**
     * Returns every export from the given cursor on, newest first. See {@link #allExports(int, String, String,
     * String)}.
     *
     * @throws IllegalArgumentException if the limit is less than 1
     */
    public Iterable<Export> allExports(int limit, @Nullable String cursor) {
        return exports.all(limit, cursor, null, null);
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
     * @param day a UTC day such as {@code 2026-05-03}, from {@link ExportId#daily}, to list only its exports, or
     *     null for every day
     * @param variant {@link ExportVariant#LITE} to list lite exports, or null for full ones
     * @throws IllegalArgumentException if the limit is less than 1
     */
    public Iterable<Export> allExports(
            int limit, @Nullable String cursor, @Nullable String day, @Nullable String variant) {
        return exports.all(limit, cursor, day, variant);
    }

    /** Returns the rows and bytes exported per UTC day over the API's default of 90 days. */
    public ExportVolume exportVolume() {
        return exports.volume(null);
    }

    /**
     * Returns the rows and bytes exported per UTC day.
     *
     * @param days how many days to cover, today included. The API allows 1 to 366.
     */
    public ExportVolume exportVolume(int days) {
        return exports.volume(days);
    }

    /**
     * Returns the metadata of an export's full file, including its checksum and Parquet columns.
     *
     * @param id an export ID from {@link Export#id()} or {@link ExportId}
     * @throws IllegalArgumentException if the ID is invalid; no request is sent
     */
    public ExportMeta exportMeta(String id) {
        return exportMeta(id, null);
    }

    /**
     * Returns the metadata of an export, including its checksum and Parquet columns.
     *
     * @param id an export ID from {@link Export#id()} or {@link ExportId}
     * @param variant {@link ExportVariant#LITE} for the lite file, which is published shortly after the full one,
     *     or null for the full file
     * @throws IllegalArgumentException if the ID is invalid; no request is sent
     */
    public ExportMeta exportMeta(String id, @Nullable String variant) {
        return exports.meta(id, "variant", variant);
    }

    /**
     * Returns the presigned URL of an export's full Parquet file. It is valid for 24 hours and grants access to
     * the file without an API key, so treat it as a secret.
     *
     * @param id an export ID from {@link Export#id()} or {@link ExportId}
     * @throws IllegalArgumentException if the ID is invalid; no request is sent
     */
    public URI downloadUrl(String id) {
        return downloadUrl(id, null);
    }

    /**
     * Returns the presigned URL of an export's Parquet file. It is valid for 24 hours and grants access to the
     * file without an API key, so treat it as a secret.
     *
     * @param id an export ID from {@link Export#id()} or {@link ExportId}
     * @param variant {@link ExportVariant#LITE} for the lite file, or null for the full file
     * @throws IllegalArgumentException if the ID is invalid; no request is sent
     */
    public URI downloadUrl(String id, @Nullable String variant) {
        return exports.downloadUrl(id, "variant", variant);
    }

    /**
     * Downloads an export's full Parquet file. See {@link #download(String, String)}.
     *
     * @param id an export ID from {@link Export#id()} or {@link ExportId}
     * @throws IllegalArgumentException if the ID is invalid; no request is sent
     */
    public InputStream download(String id) {
        return download(id, null);
    }

    /**
     * Downloads an export's Parquet file. The API key is not sent to the storage host. The caller must close
     * the returned stream. Compare the file's SHA-256 with {@link Export#checksum()} to check it.
     *
     * <p>The download is not retried, and the idle timeout applies while reading.
     *
     * @param id an export ID from {@link Export#id()} or {@link ExportId}
     * @param variant {@link ExportVariant#LITE} for the lite file, or null for the full file. A lite listing's
     *     IDs do not select the lite file, so pass the variant again.
     * @throws IllegalArgumentException if the ID is invalid; no request is sent
     */
    public InputStream download(String id, @Nullable String variant) {
        return exports.download(id, "variant", variant);
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
