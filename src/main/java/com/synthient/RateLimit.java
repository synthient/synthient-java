package com.synthient;

import java.time.Duration;
import okhttp3.Headers;
import org.jspecify.annotations.Nullable;

/**
 * The state of a rate limit bucket, from the {@code RateLimit-*} response headers. Buckets belong to the
 * organization and are shared by all of its keys.
 *
 * @param limit the bucket size
 * @param reset the time until the bucket is full again
 */
public record RateLimit(int limit, int remaining, Duration reset) {

    static @Nullable RateLimit fromHeaders(Headers headers) {
        String limit = headers.get("RateLimit-Limit");
        String remaining = headers.get("RateLimit-Remaining");
        String reset = headers.get("RateLimit-Reset");
        if (limit == null || remaining == null || reset == null) {
            return null;
        }
        try {
            long seconds = Math.clamp(Long.parseLong(reset), 0, Transport.MAX_HEADER_SECONDS);
            return new RateLimit(Integer.parseInt(limit), Integer.parseInt(remaining), Duration.ofSeconds(seconds));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
