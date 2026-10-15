package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * A proxy or VPN provider seen using an IP address.
 *
 * @param name the provider name, sent as {@code provider}
 * @param type the kind of service
 * @param lastSeen when the provider was last seen using the address
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
