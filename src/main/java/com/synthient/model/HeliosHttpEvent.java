package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * An HTTP request received by a Helios honeypot through a proxy.
 *
 * @param timestamp when the request was received
 * @param tunnelId the proxy tunnel
 * @param domain the tunnel's destination domain
 * @param port the tunnel's destination port
 * @param protocol {@code http}
 * @param meta the proxy the request came through
 * @param details the parsed request, or null when absent
 * @param raw the raw request bytes as base64, or null when absent
 */
public record HeliosHttpEvent(
        @JsonProperty(value = "timestamp", required = true)
        @JsonFormat(without = JsonFormat.Feature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
        Instant timestamp,

        @JsonProperty(value = "tunnel_id", required = true) long tunnelId,
        @JsonProperty(value = "domain", required = true) String domain,
        @JsonProperty(value = "port", required = true) int port,
        @JsonProperty(value = "protocol", required = true) String protocol,
        @JsonProperty(value = "meta", required = true) HeliosMeta meta,
        @JsonProperty("details") @Nullable HttpRequestDetails details,
        @JsonProperty("raw") @Nullable String raw) {

    public HeliosHttpEvent {
        requireNonNull(timestamp, "timestamp");
        requireNonNull(domain, "domain");
        requireNonNull(protocol, "protocol");
        requireNonNull(meta, "meta");
    }
}
