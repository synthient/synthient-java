package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * A command run on a Helios Android Debug Bridge honeypot. It carries no timestamp.
 *
 * @param session the hex session ID
 * @param sequentialId the command's position in the session, starting at 1
 * @param hash a hex SHA-256 shared by every command in the session
 * @param meta the proxy the session came through, or null from sensors that do not send it yet
 */
public record HeliosAdbEvent(
        @JsonProperty("session") String session,

        @JsonProperty(value = "sequential_id", required = true)
        long sequentialId,

        @JsonProperty("command") String command,
        @JsonProperty("hash") String hash,

        @JsonProperty(value = "meta", required = true) @Nullable HeliosMeta meta) {

    public HeliosAdbEvent {
        requireNonNull(session, "session");
        requireNonNull(command, "command");
        requireNonNull(hash, "hash");
    }
}
