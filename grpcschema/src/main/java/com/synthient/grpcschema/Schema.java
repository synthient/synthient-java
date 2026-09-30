package com.synthient.grpcschema;

import static java.util.Objects.requireNonNull;

import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import java.util.List;

/**
 * The protobuf schema returned by {@link GrpcSchema#fetch(String, FetchOptions)}.
 *
 * @param endpoint the normalized {@code host:port} that was queried
 * @param symbols the service or message names that were resolved
 * @param descriptorSet the resolved files, each after the files it imports
 */
public record Schema(String endpoint, List<String> symbols, FileDescriptorSet descriptorSet) {
    public Schema {
        requireNonNull(endpoint, "endpoint");
        symbols = List.copyOf(symbols);
        requireNonNull(descriptorSet, "descriptorSet");
    }
}
