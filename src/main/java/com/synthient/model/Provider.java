package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * A proxy or VPN provider seen using an IP address.
 *
 * @param name the provider name, sent as {@code provider}
 */
public record Provider(
        @JsonProperty("provider") String name,
        @JsonProperty("type") String type,
        @JsonProperty("last_seen") Instant lastSeen) {

    public Provider {
        requireNonNull(name, "name");
        requireNonNull(type, "type");
        requireNonNull(lastSeen, "lastSeen");
    }
}
