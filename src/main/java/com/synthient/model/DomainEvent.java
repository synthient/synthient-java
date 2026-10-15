package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * A proxy request to a domain.
 *
 * @param timestamp when the request was seen
 * @param sourceIp the source address, sent as {@code source_ip_masked} but not masked
 * @param targetSubdomain the subdomain requested
 * @param port the port requested
 * @param countryCode the country of the source address
 */
public record DomainEvent(
        @JsonProperty("timestamp") Instant timestamp,
        @JsonProperty("source_ip_masked") String sourceIp,
        @JsonProperty("target_subdomain") String targetSubdomain,
        @JsonProperty(value = "port", required = true) int port,
        @JsonProperty("country_code") String countryCode) {

    public DomainEvent {
        requireNonNull(timestamp, "timestamp");
        requireNonNull(sourceIp, "sourceIp");
        requireNonNull(targetSubdomain, "targetSubdomain");
        requireNonNull(countryCode, "countryCode");
    }
}
