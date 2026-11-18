package com.synthient.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Event counts for a domain.
 *
 * @deprecated Domain lookup was removed from the Synthient API and will stop working when the endpoint is turned
 *     off. It will be removed in the next major version.
 */
@Deprecated
public record DomainStats(
        @JsonProperty(value = "total_events_30d", required = true)
        long totalEvents30d,

        @JsonProperty(value = "events_24h", required = true) long events24h) {}
