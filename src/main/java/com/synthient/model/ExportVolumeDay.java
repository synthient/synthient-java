package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

/**
 * The rows and bytes exported on one UTC day.
 *
 * @param kind {@link ExportKind#DAILY} when the day's rollup gives the totals, or {@link ExportKind#HOURLY} when
 *     they sum the hourly exports, for today or while the rollup is pending
 * @param snapshots the number of files counted: 1 for a rollup, otherwise the hourly exports so far, of 24
 */
public record ExportVolumeDay(
        @JsonProperty("date") LocalDate date,
        @JsonProperty("kind") String kind,
        @JsonProperty(value = "snapshots", required = true) int snapshots,
        @JsonProperty(value = "row_count", required = true) long rowCount,
        @JsonProperty(value = "size_bytes", required = true) long sizeBytes) {

    public ExportVolumeDay {
        requireNonNull(date, "date");
        requireNonNull(kind, "kind");
    }
}
