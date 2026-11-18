package com.synthient;

import com.synthient.model.Export;
import com.synthient.model.ExportFormat;
import com.synthient.model.ExportMeta;
import com.synthient.model.ExportPage;
import com.synthient.model.ExportVolume;
import java.io.InputStream;
import java.net.URI;
import org.jspecify.annotations.Nullable;

/**
 * A Synthient dataset: one export a day, with no stream. Get one from the client, such as {@link
 * SynthientClient#geolocation()}. Each dataset needs its own scope on the API key.
 *
 * <p>Export IDs are {@link ExportId#LATEST}, the newest day, or a UTC day from {@link ExportId#daily}. Datasets
 * have no hourly exports, so an hourly ID is invalid.
 *
 * <p>A day can come in several formats. Geolocation has {@link ExportFormat#PARQUET}, its own, {@link
 * ExportFormat#CSV}, and {@link ExportFormat#MMDB}. WHOIS has only {@link ExportFormat#TAR_GZ}.
 */
public final class Dataset {
    private final Exports exports;

    Dataset(Transport transport, String path) {
        exports = new Exports(transport, path, false);
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
     * @param day a UTC day such as {@code 2026-05-03}, from {@link ExportId#daily}, to list only its export, or
     *     null for every day
     * @throws IllegalArgumentException if the limit is less than 1
     */
    public ExportPage listExports(int limit, @Nullable String cursor, @Nullable String day) {
        return exports.page(limit, cursor, day, null);
    }

    /** Returns every export, newest first. See {@link Feed#allExports(int, String, String, String)}. */
    public Iterable<Export> allExports() {
        return exports.all(null, null, null, null);
    }

    /**
     * Returns every export from the given cursor on, newest first. See {@link Feed#allExports(int, String, String,
     * String)}.
     *
     * @throws IllegalArgumentException if the limit is less than 1
     */
    public Iterable<Export> allExports(int limit, @Nullable String cursor) {
        return exports.all(limit, cursor, null, null);
    }

    /**
     * Returns every export of a day, or from the given cursor on, newest first. See {@link Feed#allExports(int,
     * String, String, String)}.
     *
     * @param day a UTC day such as {@code 2026-05-03}, from {@link ExportId#daily}, to list only its export, or
     *     null for every day
     * @throws IllegalArgumentException if the limit is less than 1
     */
    public Iterable<Export> allExports(int limit, @Nullable String cursor, @Nullable String day) {
        return exports.all(limit, cursor, day, null);
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
     * Returns the metadata of an export in the dataset's own format.
     *
     * @param id {@link ExportId#LATEST}, an ID from {@link Export#id()}, or a day from {@link ExportId#daily}
     * @throws IllegalArgumentException if the ID is invalid or hourly; no request is sent
     */
    public ExportMeta exportMeta(String id) {
        return exportMeta(id, null);
    }

    /**
     * Returns the metadata of an export, including the checksum and size of the file in the given format.
     *
     * @param id {@link ExportId#LATEST}, an ID from {@link Export#id()}, or a day from {@link ExportId#daily}
     * @param format one of {@link ExportFormat}, or null for the dataset's own format
     * @throws IllegalArgumentException if the ID is invalid or hourly; no request is sent
     */
    public ExportMeta exportMeta(String id, @Nullable String format) {
        return exports.meta(id, "format", format);
    }

    /**
     * Returns the presigned URL of an export's file in the dataset's own format. See {@link #downloadUrl(String,
     * String)}.
     *
     * @throws IllegalArgumentException if the ID is invalid or hourly; no request is sent
     */
    public URI downloadUrl(String id) {
        return downloadUrl(id, null);
    }

    /**
     * Returns the presigned URL of an export's file. It is valid for 24 hours and grants access to the file without
     * an API key, so treat it as a secret.
     *
     * @param id {@link ExportId#LATEST}, an ID from {@link Export#id()}, or a day from {@link ExportId#daily}
     * @param format one of {@link ExportFormat}, or null for the dataset's own format
     * @throws IllegalArgumentException if the ID is invalid or hourly; no request is sent
     */
    public URI downloadUrl(String id, @Nullable String format) {
        return exports.downloadUrl(id, "format", format);
    }

    /**
     * Downloads an export's file in the dataset's own format. See {@link #download(String, String)}.
     *
     * @throws IllegalArgumentException if the ID is invalid or hourly; no request is sent
     */
    public InputStream download(String id) {
        return download(id, null);
    }

    /**
     * Downloads an export's file as served, so a {@code csv} stays gzip-compressed and WHOIS stays a {@code tar.gz}
     * archive. The API key is not sent to the storage host. The caller must close the returned stream. Compare the
     * file's SHA-256 with the checksum of its format in {@link Export#formats()} to check it.
     *
     * <p>The download is not retried, and the idle timeout applies while reading.
     *
     * @param id {@link ExportId#LATEST}, an ID from {@link Export#id()}, or a day from {@link ExportId#daily}
     * @param format one of {@link ExportFormat}, or null for the dataset's own format
     * @throws IllegalArgumentException if the ID is invalid or hourly; no request is sent
     */
    public InputStream download(String id, @Nullable String format) {
        return exports.download(id, "format", format);
    }
}
