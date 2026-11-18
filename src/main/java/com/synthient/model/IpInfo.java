package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * Intelligence about one IP address.
 *
 * @param ip the address, as canonicalized by the API
 * @param network the network the address belongs to, or null when the address is in no known network range or
 *     the API's enrichment lookup failed
 * @param location the geolocation of the address, or null when unknown, as it is whenever {@code network} is null
 * @param intelligence risk, category, and provider data, or null when the API has none
 */
public record IpInfo(
        @JsonProperty("ip") String ip,
        @JsonProperty("network") @Nullable Network network,
        @JsonProperty("location") @Nullable Location location,
        @JsonProperty("intelligence") @Nullable Intelligence intelligence) {

    public IpInfo {
        requireNonNull(ip, "ip");
    }
}
