package com.synthient;

import org.jspecify.annotations.Nullable;

/**
 * The parent of every exception the SDK throws for a failed call. Thrown directly, it means the API sent a
 * response the SDK could not use, such as invalid JSON or a body over the size limit.
 */
public class SynthientException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SynthientException(String message) {
        super(message);
    }

    public SynthientException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
