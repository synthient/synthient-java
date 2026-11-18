package com.synthient.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Events targeting one port.
 *
 * @deprecated Domain lookup was removed from the Synthient API and will stop working when the endpoint is turned
 *     off. It will be removed in the next major version.
 */
@Deprecated
public record PortCount(
        @JsonProperty(value = "port", required = true) int port,
        @JsonProperty(value = "count", required = true) long count) {}
