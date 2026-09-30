package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * The network an IP address belongs to.
 *
 * @param asn the autonomous system number
 * @param isp the internet service provider
 * @param type the kind of network, one of {@link NetworkType}
 * @param org the organization, or null when unknown
 * @param domain the organization's domain, or null when unknown
 * @param abuseEmail the abuse contact email, or null when unknown
 * @param abusePhone the abuse contact phone number, or null when unknown
 */
public record Network(
        @JsonProperty(value = "asn", required = true) long asn,
        @JsonProperty(value = "isp", required = true) String isp,
        @JsonProperty(value = "type", required = true) String type,
        @JsonProperty("org") @Nullable String org,
        @JsonProperty("domain") @Nullable String domain,
        @JsonProperty("abuse_email") @Nullable String abuseEmail,
        @JsonProperty("abuse_phone") @Nullable String abusePhone) {

    public Network {
        requireNonNull(isp, "isp");
        requireNonNull(type, "type");
    }
}
