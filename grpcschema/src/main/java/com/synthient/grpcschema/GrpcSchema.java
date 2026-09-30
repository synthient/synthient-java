package com.synthient.grpcschema;

import com.google.protobuf.ByteString;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.reflection.v1.ServerReflectionGrpc;
import io.grpc.reflection.v1.ServerReflectionRequest;
import io.grpc.reflection.v1.ServerReflectionResponse;
import io.grpc.reflection.v1.ServiceResponse;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Fetches the protobuf schema of the Synthient gRPC API, {@code synthient.v1.SynthientService}, using gRPC
 * server reflection.
 *
 * {@snippet :
 * Schema schema = GrpcSchema.fetch(System.getenv("SYNTHIENT_API_KEY"));
 * for (FileDescriptorProto file : schema.descriptorSet().getFileList()) {
 *     System.out.println(file.getName());
 * }
 * }
 */
public final class GrpcSchema {
    /** The address of the Synthient gRPC API. */
    public static final String DEFAULT_ENDPOINT = "grpc.synthient.com:443";

    private static final int MAX_MESSAGE_SIZE = 32 << 20;
    private static final Metadata.Key<String> API_KEY = Metadata.Key.of("x-api-key", Metadata.ASCII_STRING_MARSHALLER);

    private GrpcSchema() {}

    /** Fetches every service's schema from {@link #DEFAULT_ENDPOINT}. See {@link #fetch(String, FetchOptions)}. */
    public static Schema fetch(String apiKey) {
        return fetch(apiKey, FetchOptions.builder().build());
    }

    /**
     * Fetches the protobuf files that define the requested symbols, or every listed service, along with all of
     * their imports. The API key is sent as {@code x-api-key} metadata unless it is blank.
     *
     * @throws SchemaException if reflection fails; {@link #explain(Throwable)} describes common causes
     * @throws IllegalArgumentException if the endpoint is invalid
     */
    public static Schema fetch(String apiKey, FetchOptions options) {
        Endpoint endpoint = normalizeEndpoint(options.endpoint());
        ManagedChannelBuilder<?> builder =
                ManagedChannelBuilder.forTarget(endpoint.address()).maxInboundMessageSize(MAX_MESSAGE_SIZE);
        if (options.plaintext()) {
            builder.usePlaintext();
        } else {
            builder.useTransportSecurity();
        }
        ManagedChannel channel = builder.build();
        try {
            Metadata headers = new Metadata();
            if (!apiKey.isBlank()) {
                headers.put(API_KEY, apiKey.strip());
            }
            ServerReflectionGrpc.ServerReflectionStub stub = ServerReflectionGrpc.newStub(
                            ClientInterceptors.intercept(channel, MetadataUtils.newAttachHeadersInterceptor(headers)))
                    .withDeadlineAfter(options.timeout().toNanos(), TimeUnit.NANOSECONDS);
            Reflector reflector = new Reflector(stub, endpoint.host());

            List<String> symbols = options.symbols();
            if (symbols.isEmpty()) {
                symbols = reflector.listServices(options.includeReflection());
            }
            if (symbols.isEmpty()) {
                throw new SchemaException("the server lists no services");
            }
            Map<String, FileDescriptorProto> files = new TreeMap<>();
            for (String symbol : symbols) {
                addFiles(
                        files,
                        reflector.files(ServerReflectionRequest.newBuilder().setFileContainingSymbol(symbol)));
            }
            for (List<String> missing = missingDependencies(files);
                    !missing.isEmpty();
                    missing = missingDependencies(files)) {
                for (String name : missing) {
                    addFiles(
                            files,
                            reflector.files(ServerReflectionRequest.newBuilder().setFileByFilename(name)));
                    if (!files.containsKey(name)) {
                        throw new SchemaException("reflection did not return dependency " + name);
                    }
                }
            }
            reflector.requests.onCompleted();
            return new Schema(
                    endpoint.address(),
                    symbols,
                    FileDescriptorSet.newBuilder()
                            .addAllFile(orderedFiles(files))
                            .build());
        } finally {
            channel.shutdownNow();
        }
    }

    /**
     * Converts an endpoint to {@code host:port} form. It accepts {@code host:port}, a bare host (port 443 is
     * assumed), and {@code grpc://} or {@code https://} URLs. A blank endpoint means {@link #DEFAULT_ENDPOINT}.
     *
     * @throws IllegalArgumentException if the endpoint has no host
     */
    public static Endpoint normalizeEndpoint(String raw) {
        String value = raw.strip();
        if (value.isEmpty()) {
            value = DEFAULT_ENDPOINT;
        }
        if (value.contains("://")) {
            try {
                String authority = new URI(value).getRawAuthority();
                value = authority == null ? "" : authority;
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("invalid endpoint \"" + raw + "\"", e);
            }
        }
        if (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.isEmpty()) {
            throw new IllegalArgumentException("empty endpoint \"" + raw + "\"");
        }
        int close = value.indexOf(']');
        if (value.startsWith("[") && close > 0 && value.startsWith(":", close + 1)) {
            return new Endpoint(value, value.substring(1, close));
        }
        int colon = value.indexOf(':');
        if (!value.startsWith("[") && colon >= 0 && colon == value.lastIndexOf(':')) {
            return new Endpoint(value, value.substring(0, colon));
        }
        String host = value.replaceAll("^\\[+|]+$", "");
        return new Endpoint(host.contains(":") ? "[" + host + "]:443" : host + ":443", host);
    }

    /** Returns an explanation of a common failure of {@link #fetch}, or an empty string if it has none. */
    public static String explain(Throwable error) {
        Status.Code code = error instanceof SchemaException schema
                ? schema.code()
                : Status.fromThrowable(error).getCode();
        if (code == null) {
            return "";
        }
        return switch (code) {
            case UNAUTHENTICATED -> "Missing or invalid API key.";
            case PERMISSION_DENIED -> "This API key does not have permission to inspect gRPC schemas.";
            case NOT_FOUND ->
                "No protobuf schema found for the requested symbol. Fetch without symbols to list the available"
                        + " services.";
            case UNIMPLEMENTED -> "The gRPC endpoint does not expose server reflection.";
            case UNAVAILABLE ->
                "Could not reach the gRPC endpoint. Check the address, network access, and TLS settings.";
            case DEADLINE_EXCEEDED -> "Timed out reading protobuf schemas from gRPC reflection. Use a longer timeout.";
            default -> "";
        };
    }

    private static void addFiles(Map<String, FileDescriptorProto> files, List<ByteString> raw) {
        for (ByteString data : raw) {
            FileDescriptorProto file;
            try {
                file = FileDescriptorProto.parseFrom(data);
            } catch (InvalidProtocolBufferException e) {
                throw new SchemaException("invalid file descriptor", null, e);
            }
            if (file.getName().isEmpty()) {
                throw new SchemaException("reflection returned a file descriptor without a name");
            }
            files.putIfAbsent(file.getName(), file);
        }
    }

    static List<String> missingDependencies(Map<String, FileDescriptorProto> files) {
        Set<String> missing = new TreeSet<>();
        for (FileDescriptorProto file : files.values()) {
            for (String dependency : file.getDependencyList()) {
                if (!files.containsKey(dependency)) {
                    missing.add(dependency);
                }
            }
        }
        return List.copyOf(missing);
    }

    /** Orders files so each comes after the files it imports, breaking ties by name. */
    static List<FileDescriptorProto> orderedFiles(Map<String, FileDescriptorProto> files) {
        List<FileDescriptorProto> ordered = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        for (String name : new TreeSet<>(files.keySet())) {
            visit(name, files, visited, ordered);
        }
        return ordered;
    }

    private static void visit(
            String name,
            Map<String, FileDescriptorProto> files,
            Set<String> visited,
            List<FileDescriptorProto> ordered) {
        FileDescriptorProto file = files.get(name);
        if (file == null || !visited.add(name)) {
            return;
        }
        for (String dependency : new TreeSet<>(file.getDependencyList())) {
            visit(dependency, files, visited, ordered);
        }
        ordered.add(file);
    }

    /** Sends reflection requests one at a time on a single stream and waits for each response. */
    private static final class Reflector {
        private final BlockingQueue<Object> responses = new LinkedBlockingQueue<>();
        private final StreamObserver<ServerReflectionRequest> requests;
        private final String host;

        Reflector(ServerReflectionGrpc.ServerReflectionStub stub, String host) {
            this.host = host;
            requests = stub.serverReflectionInfo(new StreamObserver<>() {
                @Override
                public void onNext(ServerReflectionResponse response) {
                    responses.add(response);
                }

                @Override
                public void onError(Throwable error) {
                    responses.add(error);
                }

                @Override
                public void onCompleted() {
                    responses.add(Status.UNAVAILABLE
                            .withDescription("the reflection stream ended early")
                            .asRuntimeException());
                }
            });
        }

        List<String> listServices(boolean includeReflection) {
            ServerReflectionResponse response =
                    call(ServerReflectionRequest.newBuilder().setListServices(""));
            if (!response.hasListServicesResponse()) {
                throw new SchemaException("reflection response has no service list");
            }
            Set<String> services = new TreeSet<>();
            for (ServiceResponse service : response.getListServicesResponse().getServiceList()) {
                String name = service.getName().strip();
                if (!name.isEmpty() && (includeReflection || !name.startsWith("grpc.reflection."))) {
                    services.add(name);
                }
            }
            return List.copyOf(services);
        }

        List<ByteString> files(ServerReflectionRequest.Builder request) {
            ServerReflectionResponse response = call(request);
            if (!response.hasFileDescriptorResponse()) {
                throw new SchemaException("reflection response has no file descriptors");
            }
            return response.getFileDescriptorResponse().getFileDescriptorProtoList();
        }

        private ServerReflectionResponse call(ServerReflectionRequest.Builder request) {
            requests.onNext(request.setHost(host).build());
            Object response;
            try {
                response = responses.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                requests.onError(Status.CANCELLED.withDescription("interrupted").asRuntimeException());
                throw new SchemaException("interrupted while fetching the schema", Status.Code.CANCELLED, e);
            }
            if (response instanceof Throwable error) {
                Status status = Status.fromThrowable(error);
                throw new SchemaException("reflection failed: " + status.getCode(), status.getCode(), error);
            }
            ServerReflectionResponse reflection = (ServerReflectionResponse) response;
            if (reflection.hasErrorResponse()) {
                Status.Code code = Status.fromCodeValue(
                                reflection.getErrorResponse().getErrorCode())
                        .getCode();
                throw new SchemaException("reflection failed: " + code, code, null);
            }
            return reflection;
        }
    }
}
