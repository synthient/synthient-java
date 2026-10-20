package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Metadata of a Parquet export.
 *
 * @param stream the feed's stream name
 * @param kind whether the export covers an hour or a day, one of {@link ExportKind}
 * @param format the file format, {@code parquet}
 * @param date the start of the hour or day the export covers
 * @param hour the hour the export covers, or null for a daily export
 * @param size the file size in bytes
 * @param checksum the hex SHA-256 of the file
 */
public record ExportMeta(
        @JsonProperty("id") String id,
        @JsonProperty("stream") String stream,
        @JsonProperty("kind") String kind,
        @JsonProperty("format") String format,
        @JsonProperty("date") Instant date,
        @JsonProperty("hour") @Nullable Integer hour,
        @JsonProperty(value = "rows", required = true) long rows,
        @JsonProperty(value = "size", required = true) long size,
        @JsonProperty("checksum") String checksum,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("schema") ParquetSchema schema) {

    public ExportMeta {
        requireNonNull(id, "id");
        requireNonNull(stream, "stream");
        requireNonNull(kind, "kind");
        requireNonNull(format, "format");
        requireNonNull(date, "date");
        requireNonNull(checksum, "checksum");
        requireNonNull(createdAt, "createdAt");
        requireNonNull(schema, "schema");
    }
}
