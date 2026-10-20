package com.synthient.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * An event from the anonymizers feed: an address range used by a VPN or other anonymizing service. Every
 * field may be absent.
 *
 * @param rangeStart the first address of the range
 * @param rangeEnd the last address of the range
 * @param type the kind of service, one of {@link ProxyType}
 * @param countryCode the ISO 3166-1 alpha-2 country code of {@code rangeStart}
 */
public record AnonymizerEvent(
        @JsonProperty("range_start") @Nullable String rangeStart,
        @JsonProperty("range_end") @Nullable String rangeEnd,
        @JsonProperty("provider") @Nullable String provider,
        @JsonProperty("type") @Nullable String type,

        @JsonProperty("timestamp")
        @JsonFormat(without = JsonFormat.Feature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
        @Nullable Instant timestamp,

        @JsonProperty("country_code") @Nullable String countryCode,
        @JsonProperty("asn") @Nullable Long asn) {}
