package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Events from one source country. */
public record CountryCount(
        @JsonProperty("country_code") String countryCode,
        @JsonProperty(value = "unique_ips", required = true) long uniqueIps,
        @JsonProperty(value = "events", required = true) long events) {

    public CountryCount {
        requireNonNull(countryCode, "countryCode");
    }
}
