package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The geolocation of an IP address.
 *
 * @param country the country code
 * @param state the state or region
 * @param city the city
 * @param timezone the IANA time zone
 * @param latitude the latitude in degrees
 * @param longitude the longitude in degrees
 * @param geoHash the geohash of the coordinates
 */
public record Location(
        @JsonProperty(value = "country", required = true) String country,
        @JsonProperty(value = "state", required = true) String state,
        @JsonProperty(value = "city", required = true) String city,
        @JsonProperty(value = "timezone", required = true) String timezone,
        @JsonProperty(value = "latitude", required = true) double latitude,
        @JsonProperty(value = "longitude", required = true) double longitude,
        @JsonProperty(value = "geo_hash", required = true) String geoHash) {

    public Location {
        requireNonNull(country, "country");
        requireNonNull(state, "state");
        requireNonNull(city, "city");
        requireNonNull(timezone, "timezone");
        requireNonNull(geoHash, "geoHash");
    }
}
