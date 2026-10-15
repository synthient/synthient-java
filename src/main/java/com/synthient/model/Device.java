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
        @JsonProperty("os") String os,
        @JsonProperty("version") String version,
        @JsonProperty("last_seen") Instant lastSeen) {

    public Device {
        requireNonNull(os, "os");
        requireNonNull(version, "version");
        requireNonNull(lastSeen, "lastSeen");
    }
}
