package com.synthient;

import org.jspecify.annotations.Nullable;

/**
 * The API sent nothing for longer than the client's {@link SynthientClient.Builder#timeout(java.time.Duration)
 * timeout}, either while waiting for the response headers or between parts of the body.
 */
public class ApiTimeoutException extends ApiConnectionException {
    private static final long serialVersionUID = 1L;

    public ApiTimeoutException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
