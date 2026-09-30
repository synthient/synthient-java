package com.synthient.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * An event from the anonymizers feed: an address range used by a VPN or other anonymizing service. Every
 * field may be absent.
 *
 * @param rangeStart the first address of the range
 * @param rangeEnd the last address of the range
 * @param provider the service provider
 * @param type the kind of service, one of {@link ProxyType}
 * @param timestamp when the range was seen
 * @param countryCode the country of the range
 * @param asn the autonomous system number of the range
 */
public record AnonymizerEvent(
        @JsonProperty("range_start") @Nullable String rangeStart,
        @JsonProperty("range_end") @Nullable String rangeEnd,
        @JsonProperty("provider") @Nullable String provider,
        @JsonProperty("type") @Nullable String type,
        @JsonProperty("timestamp") @Nullable Instant timestamp,
        @JsonProperty("country_code") @Nullable String countryCode,
        @JsonProperty("asn") @Nullable Long asn) {}
