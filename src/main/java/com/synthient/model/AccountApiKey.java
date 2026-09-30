package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * The API key used for a request. It does not include the secret.
 *
 * @param id the key ID
 * @param name the key's name
 * @param createdAt when the key was created
 */
public record AccountApiKey(
        @JsonProperty(value = "id", required = true) String id,
        @JsonProperty(value = "name", required = true) String name,
        @JsonProperty(value = "created_at", required = true) Instant createdAt) {

    public AccountApiKey {
        requireNonNull(id, "id");
        requireNonNull(name, "name");
        requireNonNull(createdAt, "createdAt");
    }
}
