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
        @JsonProperty(value = "provider", required = true) String name,
        @JsonProperty(value = "type", required = true) String type,
        @JsonProperty(value = "last_seen", required = true) Instant lastSeen) {

    public Provider {
        requireNonNull(name, "name");
        requireNonNull(type, "type");
        requireNonNull(lastSeen, "lastSeen");
    }
}
