package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Risk, category, and provider data for an IP address.
 *
 * @param riskScore the risk score from 0 to 100
 */
public record Intelligence(
        @JsonProperty(value = "risk_score", required = true) int riskScore,
        @JsonProperty("devices") List<Device> devices,
        @JsonProperty("behavior") List<String> behavior,
        @JsonProperty("categories") List<String> categories,
        @JsonProperty("providers") List<Provider> providers) {

    public Intelligence {
        devices = List.copyOf(requireNonNull(devices, "devices"));
        behavior = List.copyOf(requireNonNull(behavior, "behavior"));
        categories = List.copyOf(requireNonNull(categories, "categories"));
        providers = List.copyOf(requireNonNull(providers, "providers"));
    }
}
