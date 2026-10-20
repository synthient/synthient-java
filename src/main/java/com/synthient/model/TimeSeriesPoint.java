package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Event counts for one day.
 *
 * @param date the start of the day in UTC
 */
public record TimeSeriesPoint(
        @JsonProperty("date") Instant date,
        @JsonProperty(value = "events", required = true) long events,
        @JsonProperty(value = "unique_ips", required = true) long uniqueIps) {

    public TimeSeriesPoint {
        requireNonNull(date, "date");
    }
}
