package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Events targeting one subdomain.
 *
 * @param subdomain the subdomain
 * @param count events targeting it
 */
public record SubdomainCount(
        @JsonProperty(value = "subdomain", required = true) String subdomain,
        @JsonProperty(value = "count", required = true) long count) {

    public SubdomainCount {
        requireNonNull(subdomain, "subdomain");
    }
}
