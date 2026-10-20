package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Events targeting one subdomain. */
public record SubdomainCount(
        @JsonProperty("subdomain") String subdomain,
        @JsonProperty(value = "count", required = true) long count) {

    public SubdomainCount {
        requireNonNull(subdomain, "subdomain");
    }
}
