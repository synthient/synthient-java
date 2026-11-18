package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Events targeting one subdomain.
 *
 * @deprecated Domain lookup was removed from the Synthient API and will stop working when the endpoint is turned
 *     off. It will be removed in the next major version.
 */
@Deprecated
public record SubdomainCount(
        @JsonProperty("subdomain") String subdomain,
        @JsonProperty(value = "count", required = true) long count) {

    public SubdomainCount {
        requireNonNull(subdomain, "subdomain");
    }
}
