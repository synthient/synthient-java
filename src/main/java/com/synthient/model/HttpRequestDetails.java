package com.synthient.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A parsed HTTP request. Every field may be absent.
 *
 * @param method the request method
 * @param uri the request target
 * @param version the HTTP version
 * @param headers the request headers
 */
public record HttpRequestDetails(
        @JsonProperty("method") @Nullable String method,
        @JsonProperty("uri") @Nullable String uri,
        @JsonProperty("version") @Nullable String version,
        @JsonProperty("headers") @Nullable Map<String, String> headers) {

    public HttpRequestDetails {
        headers = headers == null ? null : Map.copyOf(headers);
    }
}
