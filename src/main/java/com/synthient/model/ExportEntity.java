package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * A Parquet file inside a WHOIS export's archive.
 *
 * @param entity the registry entity the file holds, one of {@link WhoisEntity}
 * @param checksum the hex SHA-256 of the Parquet file
 * @param schema the file's columns, or null in a listing, which leaves them out
 */
public record ExportEntity(
        @JsonProperty("entity") String entity,
        @JsonProperty(value = "row_count", required = true) long rowCount,
        @JsonProperty(value = "size_bytes", required = true) long sizeBytes,
        @JsonProperty("checksum") String checksum,
        @JsonProperty("schema") @Nullable ParquetSchema schema) {

    public ExportEntity {
        requireNonNull(entity, "entity");
        requireNonNull(checksum, "checksum");
    }
}
