package com.synthient;

import org.jspecify.annotations.Nullable;

/**
 * No response arrived: the connection failed, was lost, or the calling thread was interrupted. After an
 * interruption the thread's interrupt flag is set again.
 *
 * <p>Reading the stream returned by {@link Feed#download(String)} throws {@link java.io.IOException} instead.
 */
public class ApiConnectionException extends SynthientException {
    private static final long serialVersionUID = 1L;

    public ApiConnectionException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
