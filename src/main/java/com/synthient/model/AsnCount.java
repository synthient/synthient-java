package com.synthient.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Events from one source network.
 *
 * @param asn the autonomous system number
 * @param events events from it
 */
public record AsnCount(
        @JsonProperty(value = "asn", required = true) long asn,
        @JsonProperty(value = "events", required = true) long events) {}
