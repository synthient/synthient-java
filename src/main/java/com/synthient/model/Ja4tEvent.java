package com.synthient.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * An event from the JA4T feed: a TCP fingerprint seen from a proxy. Every field may be absent.
 *
 * @param ip the proxy's address
 * @param type the kind of service, one of {@link ProxyType}
 * @param countryCode the ISO 3166-1 alpha-2 country code of the address
 */
public record Ja4tEvent(
        @JsonProperty("ip") @Nullable String ip,
        @JsonProperty("ja4t") @Nullable String ja4t,
        @JsonProperty("provider") @Nullable String provider,
        @JsonProperty("type") @Nullable String type,
        @JsonProperty("timestamp") @Nullable Instant timestamp,
        @JsonProperty("country_code") @Nullable String countryCode,
        @JsonProperty("asn") @Nullable Long asn) {}
