package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * A TLS handshake received by a Helios honeypot through a proxy.
 *
 * @param timestamp when the handshake was received
 * @param tunnelId the proxy tunnel
 * @param domain the tunnel's destination domain
 * @param port the tunnel's destination port
 * @param protocol {@code https}
 * @param meta the proxy the handshake came through
 * @param details the parsed ClientHello, or null when the sensor could not parse it
 * @param raw always null: the HTTPS sensors do not capture handshake bytes
 */
public record HeliosHttpsEvent(
        @JsonProperty(value = "timestamp", required = true) Instant timestamp,
        @JsonProperty(value = "tunnel_id", required = true) long tunnelId,
        @JsonProperty(value = "domain", required = true) String domain,
        @JsonProperty(value = "port", required = true) int port,
        @JsonProperty(value = "protocol", required = true) String protocol,
        @JsonProperty(value = "meta", required = true) HeliosMeta meta,
        @JsonProperty("details") @Nullable TlsClientHello details,
        @JsonProperty("raw") @Nullable String raw) {

    public HeliosHttpsEvent {
        requireNonNull(timestamp, "timestamp");
        requireNonNull(domain, "domain");
        requireNonNull(protocol, "protocol");
        requireNonNull(meta, "meta");
    }
}
