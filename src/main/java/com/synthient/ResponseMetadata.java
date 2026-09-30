package com.synthient;

import static java.util.Objects.requireNonNullElse;

import okhttp3.Headers;
import org.jspecify.annotations.Nullable;

/**
 * Metadata of one API response, passed to the {@link SynthientClient.Builder#responseHook response hook}.
 *
 * @param statusCode the HTTP status
 * @param requestId the {@code X-Request-Id} header, or an empty string
 * @param rateLimit the rate limit state, or null when the response has no complete {@code RateLimit-*}
 *     headers
 * @param headers all response headers. The 307 of an export download carries the presigned URL in {@code
 *     Location}, which grants access to the file, so do not log the headers.
 */
public record ResponseMetadata(
        int statusCode, String requestId, @Nullable RateLimit rateLimit, Headers headers) {

    static ResponseMetadata of(int statusCode, Headers headers) {
        return new ResponseMetadata(
                statusCode,
                requireNonNullElse(headers.get("X-Request-Id"), ""),
                RateLimit.fromHeaders(headers),
                headers);
    }

    /** Returns the status, request ID, and rate limit, leaving out the headers. */
    @Override
    public String toString() {
        return "ResponseMetadata[statusCode=" + statusCode + ", requestId=" + requestId + ", rateLimit=" + rateLimit
                + "]";
    }
}
