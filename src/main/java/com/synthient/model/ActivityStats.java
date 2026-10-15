package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Hourly activity statistics for a domain, in UTC.
 *
 * @param peakHour the busiest hour of the day, 0 to 23
 * @param quietHour the quietest hour of the day, 0 to 23
 * @param medianPerHour the median events per hour
 * @param p95PerHour the 95th percentile of events per hour
 * @param cadence a sentence describing the activity pattern, one of {@link Cadence}
 */
public record ActivityStats(
        @JsonProperty(value = "peak_hour", required = true) int peakHour,
        @JsonProperty(value = "quiet_hour", required = true) int quietHour,

        @JsonProperty(value = "median_per_hour", required = true)
        long medianPerHour,

        @JsonProperty(value = "p95_per_hour", required = true)
        long p95PerHour,

        @JsonProperty("cadence") String cadence) {

    public ActivityStats {
        requireNonNull(cadence, "cadence");
    }
}
