package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The geolocation of an IP address.
 *
 * @param country the country code
 * @param state the state or region
 * @param timezone the IANA time zone
 * @param latitude the latitude in degrees
 * @param longitude the longitude in degrees
 */
public record Location(
        @JsonProperty("country") String country,
        @JsonProperty("state") String state,
        @JsonProperty("city") String city,
        @JsonProperty("timezone") String timezone,
        @JsonProperty(value = "latitude", required = true) double latitude,
        @JsonProperty(value = "longitude", required = true) double longitude,
        @JsonProperty("geo_hash") String geoHash) {

    public Location {
        requireNonNull(country, "country");
        requireNonNull(state, "state");
        requireNonNull(city, "city");
        requireNonNull(timezone, "timezone");
        requireNonNull(geoHash, "geoHash");
    }
}
