package com.synthient.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Events from one source network.
 *
 * @deprecated Domain lookup was removed from the Synthient API and will stop working when the endpoint is turned
 *     off. It will be removed in the next major version.
 */
@Deprecated
public record AsnCount(
        @JsonProperty(value = "asn", required = true) long asn,
        @JsonProperty(value = "events", required = true) long events) {}
