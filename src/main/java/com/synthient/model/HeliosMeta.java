package com.synthient.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * The proxy a Helios honeypot request came through. Every field may be absent.
 *
 * @param proxyIp the proxy's exit address
 * @param server the honeypot server that received the request
 */
public record HeliosMeta(
        @JsonProperty("proxy_ip") @Nullable String proxyIp,
        @JsonProperty("server") @Nullable String server,
        @JsonProperty("pool_id") @Nullable String poolId,
        @JsonProperty("provider") @Nullable String provider) {}
