package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A command run on a Helios Android Debug Bridge honeypot. It carries no timestamp.
 *
 * @param session the hex session ID
 * @param sequentialId the command's position in the session, starting at 1
 * @param command the command text
 * @param hash a hex SHA-256 shared by every command in the session
 */
public record HeliosAdbEvent(
        @JsonProperty(value = "session", required = true) String session,

        @JsonProperty(value = "sequential_id", required = true)
        long sequentialId,

        @JsonProperty(value = "command", required = true) String command,
        @JsonProperty(value = "hash", required = true) String hash) {

    public HeliosAdbEvent {
        requireNonNull(session, "session");
        requireNonNull(command, "command");
        requireNonNull(hash, "hash");
    }
}
