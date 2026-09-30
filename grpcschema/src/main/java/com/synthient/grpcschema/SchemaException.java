package com.synthient.grpcschema;

import com.synthient.SynthientException;
import io.grpc.Status;
import org.jspecify.annotations.Nullable;

/**
 * Fetching a schema failed. {@link GrpcSchema#explain(Throwable)} describes the common causes.
 */
public class SchemaException extends SynthientException {
    private static final long serialVersionUID = 1L;

    private final Status.@Nullable Code code;

    SchemaException(String message) {
        this(message, null, null);
    }

    public SchemaException(String message, Status.@Nullable Code code, @Nullable Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /** Returns the gRPC status code of the failure, or null when the failure did not come from gRPC. */
    public Status.@Nullable Code code() {
        return code;
    }
}
