package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Events from one source country.
 *
 * @deprecated Domain lookup was removed from the Synthient API and will stop working when the endpoint is turned
 *     off. It will be removed in the next major version.
 */
@Deprecated
public record CountryCount(
        @JsonProperty("country_code") String countryCode,
        @JsonProperty(value = "unique_ips", required = true) long uniqueIps,
        @JsonProperty(value = "events", required = true) long events) {

    public CountryCount {
        requireNonNull(countryCode, "countryCode");
    }
}
