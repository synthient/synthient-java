package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One file of an export, in one format.
 *
 * @param format one of {@link ExportFormat}
 * @param checksum the hex SHA-256 of the file as downloaded, so of the compressed file for {@code csv}
 */
public record ExportFile(
        @JsonProperty("format") String format,
        @JsonProperty(value = "size_bytes", required = true) long sizeBytes,
        @JsonProperty("checksum") String checksum) {

    public ExportFile {
        requireNonNull(format, "format");
        requireNonNull(checksum, "checksum");
    }
}
