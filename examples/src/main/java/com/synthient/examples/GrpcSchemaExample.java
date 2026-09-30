package com.synthient.examples;

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.synthient.grpcschema.GrpcSchema;
import com.synthient.grpcschema.Schema;
import com.synthient.grpcschema.SchemaException;

/** Prints the protobuf schema of the Synthient gRPC API. Run with {@code ./gradlew -q :examples:grpcSchema}. */
public final class GrpcSchemaExample {
    public static void main(String[] args) {
        Schema schema;
        try {
            schema = GrpcSchema.fetch(System.getenv().getOrDefault("SYNTHIENT_API_KEY", ""));
        } catch (SchemaException e) {
            String explanation = GrpcSchema.explain(e);
            System.err.println(explanation.isEmpty() ? e.toString() : explanation + " " + e);
            System.exit(1);
            return;
        }

        System.out.println("endpoint: " + schema.endpoint());
        for (String symbol : schema.symbols()) {
            System.out.println("service: " + symbol);
        }
        for (FileDescriptorProto file : schema.descriptorSet().getFileList()) {
            System.out.printf("file: %s (package %s)%n", file.getName(), file.getPackage());
        }
    }

    private GrpcSchemaExample() {}
}
