package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;

/**
 * An organization's lookup credits.
 *
 * @param credits the credits remaining
 * @param resetsIn the time until the credits reset
 */
public record LookupQuota(
        @JsonProperty(value = "credits", required = true) long credits,
        @JsonProperty("resets_in") Duration resetsIn) {

    public LookupQuota {
        requireNonNull(resetsIn, "resetsIn");
    }
}
