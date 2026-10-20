package com.synthient.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * An event from the proxies feed. Every field may be absent.
 *
 * @param type the kind of service, one of {@link ProxyType}
 * @param countryCode the ISO 3166-1 alpha-2 country code of the address
 */
public record ProxyEvent(
        @JsonProperty("ip") @Nullable String ip,
        @JsonProperty("provider") @Nullable String provider,
        @JsonProperty("type") @Nullable String type,
        @JsonProperty("timestamp") @Nullable Instant timestamp,
        @JsonProperty("country_code") @Nullable String countryCode,
        @JsonProperty("asn") @Nullable Long asn) {}
