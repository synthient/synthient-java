package com.synthient.grpcschema;

/**
 * A gRPC endpoint returned by {@link GrpcSchema#normalizeEndpoint(String)}.
 *
 * @param address the {@code host:port} to connect to
 * @param host the host name, sent as the host of each reflection request. It is not used to verify TLS.
 */
record Endpoint(String address, String host) {}
