package com.synthient.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Events targeting one port.
 *
 * @param port the port
 * @param count events targeting it
 */
public record PortCount(
        @JsonProperty(value = "port", required = true) int port,
        @JsonProperty(value = "count", required = true) long count) {}
