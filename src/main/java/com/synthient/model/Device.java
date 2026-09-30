package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * A device seen behind an IP address.
 *
 * @param os the operating system, one of {@link OperatingSystem}
 * @param version the operating system version
 * @param lastSeen when the device was last seen
 */
public record Device(
        @JsonProperty(value = "os", required = true) String os,
        @JsonProperty(value = "version", required = true) String version,
        @JsonProperty(value = "last_seen", required = true) Instant lastSeen) {

    public Device {
        requireNonNull(os, "os");
        requireNonNull(version, "version");
        requireNonNull(lastSeen, "lastSeen");
    }
}
