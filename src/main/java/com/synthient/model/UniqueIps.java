package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Unique source IP counts for a domain.
 *
 * @param value24h unique IPs in the last 24 hours
 * @param value30d unique IPs in the last 30 days
 * @param sparkline24h unique IPs per hour over the last 24 hours
 */
public record UniqueIps(
        @JsonProperty(value = "value_24h", required = true) long value24h,
        @JsonProperty(value = "value_30d", required = true) long value30d,

        @JsonProperty(value = "sparkline_24h", required = true)
        List<Long> sparkline24h) {

    public UniqueIps {
        sparkline24h = List.copyOf(requireNonNull(sparkline24h, "sparkline24h"));
    }
}
