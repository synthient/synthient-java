package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/** The API key used for a request. It does not include the secret. */
public record AccountApiKey(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("created_at") Instant createdAt) {

    public AccountApiKey {
        requireNonNull(id, "id");
        requireNonNull(name, "name");
        requireNonNull(createdAt, "createdAt");
    }
}
