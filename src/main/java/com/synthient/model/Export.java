package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * An export of a feed or dataset.
 *
 * @param id the ID to pass to {@code download} and {@code exportMeta}
 * @param kind whether the export covers an hour or a day, one of {@link ExportKind}
 * @param date the day the export covers, in UTC
 * @param hour the hour the export covers, or null for a daily export
 * @param checksum the hex SHA-256 of the file
 * @param downloadPath the API path that redirects to the file
 * @param formats every file of the export, its own format first. {@code sizeBytes} and {@code checksum} are the
 *     first file's.
 * @param entities the Parquet files inside a WHOIS archive, whose rows add up to {@code rowCount}, or null for
 *     any other export
 * @param variant {@link ExportVariant#LITE} in a lite listing, otherwise null. The ID alone downloads the full
 *     file, so pass the variant again to download the lite one.
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
        @JsonProperty("download_path") String downloadPath,
        @JsonProperty("formats") List<ExportFile> formats,
        @JsonProperty("entities") @Nullable List<ExportEntity> entities,
        @JsonProperty("variant") @Nullable String variant) {

    public Export {
        requireNonNull(id, "id");
        requireNonNull(kind, "kind");
        requireNonNull(date, "date");
        requireNonNull(checksum, "checksum");
        requireNonNull(createdAt, "createdAt");
        requireNonNull(downloadPath, "downloadPath");
        formats = List.copyOf(requireNonNull(formats, "formats"));
        entities = entities == null ? null : List.copyOf(entities);
    }
}
