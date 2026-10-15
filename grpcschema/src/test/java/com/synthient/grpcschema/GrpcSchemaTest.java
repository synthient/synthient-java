package com.synthient.grpcschema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.protobuf.services.HealthStatusManager;
import io.grpc.protobuf.services.ProtoReflectionServiceV1;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class GrpcSchemaTest {
    private static final Metadata.Key<String> API_KEY = Metadata.Key.of("x-api-key", Metadata.ASCII_STRING_MARSHALLER);

    private final List<String> keys = new CopyOnWriteArrayList<>();
    private Server server;

    @BeforeEach
    void startServer() throws IOException {
        ServerInterceptor recordKeys = new ServerInterceptor() {
            @Override
            public <Q, R> ServerCall.Listener<Q> interceptCall(
                    ServerCall<Q, R> call, Metadata headers, ServerCallHandler<Q, R> next) {
                String key = headers.get(API_KEY);
                keys.add(key == null ? "" : key);
                return next.startCall(call, headers);
            }
        };
        server = ServerBuilder.forPort(0)
                .addService(new HealthStatusManager().getHealthService())
                .addService(ServerInterceptors.intercept(ProtoReflectionServiceV1.newInstance(), recordKeys))
                .build()
                .start();
    }

    @AfterEach
    void stopServer() throws InterruptedException {
        server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    private FetchOptions.Builder local() {
        return FetchOptions.builder().endpoint("127.0.0.1:" + server.getPort()).plaintext(true);
    }

    @Test
    void fetchesEveryService() {
        Schema schema = GrpcSchema.fetch(" test-key ", local().build());
        assertEquals("127.0.0.1:" + server.getPort(), schema.endpoint());
        assertEquals(List.of("grpc.health.v1.Health"), schema.symbols());
        assertTrue(schema.descriptorSet().getFileList().stream()
                .map(FileDescriptorProto::getName)
                .anyMatch("grpc/health/v1/health.proto"::equals));
        assertEquals(List.of("test-key"), keys);
    }

    @Test
    void appliesOptions() {
        Schema schema = GrpcSchema.fetch(
                "",
                local().endpoint("grpc://127.0.0.1:" + server.getPort())
                        .symbols("grpc.health.v1.Health", "grpc.reflection.v1.ServerReflection")
                        .build());
        assertEquals(List.of("grpc.health.v1.Health", "grpc.reflection.v1.ServerReflection"), schema.symbols());
        assertEquals(List.of(""), keys);

        schema = GrpcSchema.fetch(
                "test-key",
                local().symbols("grpc.health.v1.Health", " grpc.health.v1.Health ", " ")
                        .build());
        assertEquals(List.of("grpc.health.v1.Health"), schema.symbols());
    }

    @Test
    void reportsUnknownSymbols() {
        SchemaException error = assertThrows(
                SchemaException.class,
                () -> GrpcSchema.fetch(
                        "test-key", local().symbols("synthient.v1.Missing").build()));
        assertEquals(Status.Code.NOT_FOUND, error.code());
        assertTrue(GrpcSchema.explain(error).startsWith("No protobuf schema found"));
    }

    @Test
    void reportsAnUnreachableServer() throws IOException {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        FetchOptions options = FetchOptions.builder()
                .endpoint("127.0.0.1:" + port)
                .plaintext(true)
                .build();
        SchemaException error = assertThrows(SchemaException.class, () -> GrpcSchema.fetch("test-key", options));
        assertEquals(Status.Code.UNAVAILABLE, error.code());
    }

    @Test
    void timesOut() throws IOException {
        List<Socket> accepted = new CopyOnWriteArrayList<>();
        try (ServerSocket silent = new ServerSocket(0)) {
            Thread.ofVirtual().start(() -> {
                try {
                    while (true) {
                        accepted.add(silent.accept());
                    }
                } catch (IOException e) {
                    // the test closed the socket
                }
            });
            FetchOptions options = FetchOptions.builder()
                    .endpoint("127.0.0.1:" + silent.getLocalPort())
                    .plaintext(true)
                    .timeout(Duration.ofMillis(300))
                    .build();
            SchemaException error = assertThrows(SchemaException.class, () -> GrpcSchema.fetch("test-key", options));
            assertEquals(Status.Code.DEADLINE_EXCEEDED, error.code());
            assertTrue(GrpcSchema.explain(error).startsWith("Timed out"));
        } finally {
            for (Socket socket : accepted) {
                socket.close();
            }
        }
    }

    @ParameterizedTest
    @CsvSource({
        "'', grpc.synthient.com:443, grpc.synthient.com",
        "grpc.synthient.com, grpc.synthient.com:443, grpc.synthient.com",
        "localhost:50051/, localhost:50051, localhost",
        "grpc://localhost:50051, localhost:50051, localhost",
        "https://grpc.synthient.com, grpc.synthient.com:443, grpc.synthient.com",
        "'[::1]:9000', '[::1]:9000', ::1",
        "::1, '[::1]:443', ::1",
        "'[::1]', '[::1]:443', ::1"
    })
    void normalizesEndpoints(String raw, String address, String host) {
        assertEquals(new Endpoint(address, host), GrpcSchema.normalizeEndpoint(raw));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "grpc://",
                ":443",
                "localhost:0",
                "localhost:65536",
                "localhost:abc",
                "localhost:",
                "grpc://user@localhost:443",
                "https://localhost/service",
                "ftp://localhost",
                "localhost?query",
                "localhost#fragment",
                "[::1]:0",
                "[::1]:abc",
                "[::1"
            })
    void rejectsInvalidEndpoints(String endpoint) {
        assertThrows(IllegalArgumentException.class, () -> GrpcSchema.normalizeEndpoint(endpoint));
    }

    @ParameterizedTest
    @EnumSource(
            value = Status.Code.class,
            names = {
                "UNAUTHENTICATED",
                "PERMISSION_DENIED",
                "NOT_FOUND",
                "UNIMPLEMENTED",
                "UNAVAILABLE",
                "DEADLINE_EXCEEDED"
            })
    void explainsCommonFailures(Status.Code code) {
        assertFalse(
                GrpcSchema.explain(Status.fromCode(code).asRuntimeException()).isEmpty());
        assertFalse(GrpcSchema.explain(new SchemaException("x", code, null)).isEmpty());
    }

    @Test
    void explainsNothingElse() {
        assertEquals("", GrpcSchema.explain(Status.INTERNAL.asRuntimeException()));
        assertEquals("", GrpcSchema.explain(new IllegalStateException("plain")));
        assertEquals("", GrpcSchema.explain(new SchemaException("x", null, null)));
    }

    @Test
    void ordersFilesAfterTheirImports() {
        Map<String, FileDescriptorProto> files = new TreeMap<>();
        files.put("a.proto", file("a.proto", "b.proto", "c.proto"));
        files.put("b.proto", file("b.proto", "c.proto"));
        files.put("c.proto", file("c.proto"));
        files.put("d.proto", file("d.proto", "missing.proto"));
        assertEquals(
                List.of("c.proto", "b.proto", "a.proto", "d.proto"),
                GrpcSchema.orderedFiles(files).stream()
                        .map(FileDescriptorProto::getName)
                        .toList());
        assertEquals(List.of("missing.proto"), GrpcSchema.missingDependencies(files));
    }

    @Test
    void schemasCopyTheirSymbols() {
        List<String> symbols = new ArrayList<>(List.of("a"));
        Schema schema = new Schema("x:443", symbols, FileDescriptorSet.getDefaultInstance());
        symbols.add("b");
        assertEquals(List.of("a"), schema.symbols());
        assertNull(new SchemaException("x", null, null).code());
    }

    private static FileDescriptorProto file(String name, String... dependencies) {
        return FileDescriptorProto.newBuilder()
                .setName(name)
                .addAllDependency(List.of(dependencies))
                .build();
    }
}
