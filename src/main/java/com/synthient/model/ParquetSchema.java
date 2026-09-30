package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * The columns of a Parquet export.
 *
 * @param fields the columns in file order
 */
public record ParquetSchema(
        @JsonProperty(value = "fields", required = true) List<ParquetColumn> fields) {

    public ParquetSchema {
        fields = List.copyOf(requireNonNull(fields, "fields"));
    }
}
