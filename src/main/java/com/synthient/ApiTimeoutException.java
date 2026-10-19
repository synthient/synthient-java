package com.synthient;

import org.jspecify.annotations.Nullable;

/**
 * Connecting took longer than the connect timeout, or the API was silent for longer than the client's {@link
 * SynthientClient.Builder#timeout(java.time.Duration) timeout}.
 *
 * <p>Reading the stream returned by {@link Feed#download(String)} throws {@link java.net.SocketTimeoutException}
 * instead.
 */
public class ApiTimeoutException extends ApiConnectionException {
    private static final long serialVersionUID = 1L;

    public ApiTimeoutException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
