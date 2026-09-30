package com.synthient;

import org.jspecify.annotations.Nullable;

/**
 * No response arrived: the connection failed, was lost, or the calling thread was interrupted. After an
 * interruption the thread's interrupt flag is set again. The cause holds the underlying failure.
 */
public class ApiConnectionException extends SynthientException {
    private static final long serialVersionUID = 1L;

    public ApiConnectionException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
