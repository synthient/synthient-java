package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * A Parquet export of a feed.
 *
 * @param id the ID to pass to {@code Feed.download} and {@code Feed.exportMeta}
 * @param kind whether the export covers an hour or a day, one of {@link ExportKind}
 * @param date the day the export covers, in UTC
 * @param hour the hour the export covers, or null for a daily export
 * @param sizeBytes the file size in bytes
 * @param rowCount the number of rows
 * @param checksum the hex SHA-256 of the file
 * @param createdAt when the export was written
 * @param downloadPath the API path that redirects to the file
 */
public record Export(
        @JsonProperty("id") String id,
        @JsonProperty("kind") String kind,
        @JsonProperty("date") LocalDate date,
        @JsonProperty("hour") @Nullable Integer hour,
        @JsonProperty(value = "size_bytes", required = true) long sizeBytes,
        @JsonProperty(value = "row_count", required = true) long rowCount,
        @JsonProperty("checksum") String checksum,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("download_path") String downloadPath) {

    public Export {
        requireNonNull(id, "id");
        requireNonNull(kind, "kind");
        requireNonNull(date, "date");
        requireNonNull(checksum, "checksum");
        requireNonNull(createdAt, "createdAt");
        requireNonNull(downloadPath, "downloadPath");
    }
}
