package com.synthient.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Event counts for a domain. */
public record DomainStats(
        @JsonProperty(value = "total_events_30d", required = true)
        long totalEvents30d,

        @JsonProperty(value = "events_24h", required = true) long events24h) {}
