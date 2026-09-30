package com.synthient.grpcschema;

/**
 * A gRPC endpoint returned by {@link GrpcSchema#normalizeEndpoint(String)}.
 *
 * @param address the {@code host:port} to connect to
 * @param host the host name, used to verify the server's TLS certificate
 */
public record Endpoint(String address, String host) {}
