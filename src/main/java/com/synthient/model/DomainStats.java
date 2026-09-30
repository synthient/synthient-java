package com.synthient.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Event counts for a domain.
 *
 * @param totalEvents30d events in the last 30 days
 * @param events24h events in the last 24 hours
 */
public record DomainStats(
        @JsonProperty(value = "total_events_30d", required = true)
        long totalEvents30d,

        @JsonProperty(value = "events_24h", required = true) long events24h) {}
