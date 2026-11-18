package com.synthient.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * The columns of a Parquet export.
 *
 * @param fields the columns in file order
 */
public record ParquetSchema(@JsonProperty("fields") List<ParquetColumn> fields) {

    public ParquetSchema {
        // The API has sent null for a WHOIS archive's empty schema.
        fields = fields == null ? List.of() : List.copyOf(fields);
    }
}
