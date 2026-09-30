package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Proxy traffic to a domain over the last 30 days. A domain the API has never seen is {@link
 * DomainStatus#INACTIVE} with zeroed statistics.
 *
 * @param domain the domain
 * @param status the activity status, one of {@link DomainStatus}
 * @param stats event counts
 * @param timeSeries daily event and unique IP counts
 * @param uniqueIps unique source IP counts
 * @param topSubdomains the most targeted subdomains
 * @param topPorts the most targeted ports
 * @param geoDistribution events by source country
 * @param hourDowHeatmap event counts as 7 rows of 24 hours, row 0 being Monday, in UTC
 * @param activityStats hourly activity statistics
 * @param recentEvents the most recent events, up to the requested limit
 * @param topAsn the source network with the most events, or null when there were none
 */
public record DomainInfo(
        @JsonProperty(value = "domain", required = true) String domain,
        @JsonProperty(value = "status", required = true) String status,
        @JsonProperty(value = "stats", required = true) DomainStats stats,

        @JsonProperty(value = "time_series", required = true)
        List<TimeSeriesPoint> timeSeries,

        @JsonProperty(value = "unique_ips", required = true) UniqueIps uniqueIps,

        @JsonProperty(value = "top_subdomains", required = true)
        List<SubdomainCount> topSubdomains,

        @JsonProperty(value = "top_ports", required = true) List<PortCount> topPorts,

        @JsonProperty(value = "geo_distribution", required = true)
        List<CountryCount> geoDistribution,

        @JsonProperty(value = "hour_dow_heatmap", required = true)
        List<List<Long>> hourDowHeatmap,

        @JsonProperty(value = "activity_stats", required = true)
        ActivityStats activityStats,

        @JsonProperty(value = "recent_events", required = true)
        List<DomainEvent> recentEvents,

        @JsonProperty("top_asn") @Nullable AsnCount topAsn) {

    public DomainInfo {
        requireNonNull(domain, "domain");
        requireNonNull(status, "status");
        requireNonNull(stats, "stats");
        timeSeries = List.copyOf(requireNonNull(timeSeries, "timeSeries"));
        requireNonNull(uniqueIps, "uniqueIps");
        topSubdomains = List.copyOf(requireNonNull(topSubdomains, "topSubdomains"));
        topPorts = List.copyOf(requireNonNull(topPorts, "topPorts"));
        geoDistribution = List.copyOf(requireNonNull(geoDistribution, "geoDistribution"));
        hourDowHeatmap = requireNonNull(hourDowHeatmap, "hourDowHeatmap").stream()
                .map(List::copyOf)
                .toList();
        requireNonNull(activityStats, "activityStats");
        recentEvents = List.copyOf(requireNonNull(recentEvents, "recentEvents"));
    }
}
