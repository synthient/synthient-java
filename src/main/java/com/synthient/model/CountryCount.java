package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Events from one source country.
 *
 * @param countryCode the country code
 * @param uniqueIps unique source IPs from it
 * @param events events from it
 */
public record CountryCount(
        @JsonProperty(value = "country_code", required = true)
        String countryCode,

        @JsonProperty(value = "unique_ips", required = true) long uniqueIps,
        @JsonProperty(value = "events", required = true) long events) {

    public CountryCount {
        requireNonNull(countryCode, "countryCode");
    }
}
