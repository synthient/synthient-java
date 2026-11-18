package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Event counts for one day.
 *
 * @param date the start of the day in UTC
 * @deprecated Domain lookup was removed from the Synthient API and will stop working when the endpoint is turned
 *     off. It will be removed in the next major version.
 */
@Deprecated
public record TimeSeriesPoint(
        @JsonProperty("date") Instant date,
        @JsonProperty(value = "events", required = true) long events,
        @JsonProperty(value = "unique_ips", required = true) long uniqueIps) {

    public TimeSeriesPoint {
        requireNonNull(date, "date");
    }
}
