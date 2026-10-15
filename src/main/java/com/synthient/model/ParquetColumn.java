package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A column of a Parquet export.
 *
 * @param name the column name
 * @param type the column type, one of {@link ParquetType}
 */
public record ParquetColumn(
        @JsonProperty("name") String name,
        @JsonProperty("type") String type) {

    public ParquetColumn {
        requireNonNull(name, "name");
        requireNonNull(type, "type");
    }
}
