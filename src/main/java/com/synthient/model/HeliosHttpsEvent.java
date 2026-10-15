package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigInteger;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * A TLS handshake received by a Helios honeypot through a proxy.
 *
 * @param timestamp when the handshake was received
 * @param tunnelId the proxy tunnel, an unsigned 64-bit value that can exceed {@code Long.MAX_VALUE}
 * @param domain the tunnel's destination domain
 * @param port the tunnel's destination port
 * @param protocol {@code https}
 * @param meta the proxy the handshake came through
 * @param details the parsed ClientHello, or null when the sensor could not parse it
 * @param raw always null: the HTTPS sensors do not capture handshake bytes
 */
public record HeliosHttpsEvent(
        @JsonProperty("timestamp") @JsonFormat(without = JsonFormat.Feature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
        Instant timestamp,

        @JsonProperty("tunnel_id") BigInteger tunnelId,
        @JsonProperty("domain") String domain,
        @JsonProperty(value = "port", required = true) int port,
        @JsonProperty("protocol") String protocol,
        @JsonProperty("meta") HeliosMeta meta,
        @JsonProperty("details") @Nullable TlsClientHello details,
        @JsonProperty("raw") @Nullable String raw) {

    public HeliosHttpsEvent {
        requireNonNull(timestamp, "timestamp");
        requireNonNull(tunnelId, "tunnelId");
        requireNonNull(domain, "domain");
        requireNonNull(protocol, "protocol");
        requireNonNull(meta, "meta");
    }
}
