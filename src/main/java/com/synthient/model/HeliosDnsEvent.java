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
        @JsonProperty("timestamp") @JsonFormat(without = JsonFormat.Feature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
        Instant timestamp,

        @JsonProperty("tunnel_id") BigInteger tunnelId,
        @JsonProperty("domain") String domain,
        @JsonProperty(value = "port", required = true) int port,
        @JsonProperty("protocol") String protocol,
        @JsonProperty("meta") HeliosMeta meta) {

    public HeliosDnsEvent {
        requireNonNull(timestamp, "timestamp");
        requireNonNull(tunnelId, "tunnelId");
        requireNonNull(domain, "domain");
        requireNonNull(protocol, "protocol");
        requireNonNull(meta, "meta");
    }
}
