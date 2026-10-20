package com.synthient.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Events from one source network. */
public record AsnCount(
        @JsonProperty(value = "asn", required = true) long asn,
        @JsonProperty(value = "events", required = true) long events) {}
