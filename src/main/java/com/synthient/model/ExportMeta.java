package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Metadata of an export.
 *
 * @param stream the feed's stream name
 * @param kind whether the export covers an hour or a day, one of {@link ExportKind}
 * @param format the format {@code size} and {@code checksum} describe, one of {@link ExportFormat}: the one asked
 *     for, otherwise the export's own, which is {@code tar.gz} for WHOIS and {@code parquet} for the rest
 * @param date the start of the hour or day the export covers
 * @param hour the hour the export covers, or null for a daily export
 * @param size the file size in bytes
 * @param checksum the hex SHA-256 of the file
 * @param schema the file's columns, which are empty for a WHOIS archive
 * @param formats every file of the export, its own format first
 * @param entities the Parquet files inside a WHOIS archive, each with its columns, or null for any other export
 * @param variant {@link ExportVariant#LITE} when the lite file was asked for, otherwise null
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
        @JsonProperty("schema") ParquetSchema schema,
        @JsonProperty("formats") List<ExportFile> formats,
        @JsonProperty("entities") @Nullable List<ExportEntity> entities,
        @JsonProperty("variant") @Nullable String variant) {

    public ExportMeta {
        requireNonNull(id, "id");
        requireNonNull(stream, "stream");
        requireNonNull(kind, "kind");
        requireNonNull(format, "format");
        requireNonNull(date, "date");
        requireNonNull(checksum, "checksum");
        requireNonNull(createdAt, "createdAt");
        requireNonNull(schema, "schema");
        formats = List.copyOf(requireNonNull(formats, "formats"));
        entities = entities == null ? null : List.copyOf(entities);
    }
}
