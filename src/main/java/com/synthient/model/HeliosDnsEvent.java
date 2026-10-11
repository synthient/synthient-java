package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigInteger;
import java.time.Instant;

/**
 * A DNS tunnel opened to a Helios honeypot through a proxy.
 *
 * @param timestamp when the tunnel was opened
 * @param tunnelId the proxy tunnel, an unsigned 64-bit value that can exceed {@code Long.MAX_VALUE}
 * @param domain the tunnel's destination domain, not a DNS query name
 * @param port the tunnel's destination port
 * @param protocol {@code dns}
 * @param meta the proxy the tunnel came through
 */
public record HeliosDnsEvent(
        @JsonProperty(value = "timestamp", required = true)
        @JsonFormat(without = JsonFormat.Feature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
        Instant timestamp,

        @JsonProperty(value = "tunnel_id", required = true) BigInteger tunnelId,
        @JsonProperty(value = "domain", required = true) String domain,
        @JsonProperty(value = "port", required = true) int port,
        @JsonProperty(value = "protocol", required = true) String protocol,
        @JsonProperty(value = "meta", required = true) HeliosMeta meta) {

    public HeliosDnsEvent {
        requireNonNull(timestamp, "timestamp");
        requireNonNull(tunnelId, "tunnelId");
        requireNonNull(domain, "domain");
        requireNonNull(protocol, "protocol");
        requireNonNull(meta, "meta");
    }
}
