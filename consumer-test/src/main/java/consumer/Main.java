package consumer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.synthient.ApiException;
import com.synthient.EventStream;
import com.synthient.SynthientClient;
import com.synthient.grpcschema.FetchOptions;
import com.synthient.grpcschema.GrpcSchema;
import com.synthient.grpcschema.SchemaException;
import com.synthient.model.ProxyEvent;
import io.grpc.Status;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Uses the staged artifacts the way an application would, against a local stand-in for the API. */
public final class Main {
    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/v4/lookup/ip/", exchange -> respond(exchange, 200, "{\"ip\":\"8.8.8.8\"}"));
        server.createContext("/api/v4/account/me", exchange -> respond(exchange, 403, "{\"detail\":\"no scope\"}"));
        server.createContext(
                "/api/v4/feeds/proxies/stream", exchange -> respond(exchange, 200, "{\"ip\":\"10.0.0.1\"}\n\n{\"ip\":\"10.0.0.2\"}\n"));
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v4";

        try (SynthientClient client =
                SynthientClient.builder().apiKey("consumer-key").baseUrl(baseUrl).build()) {
            check(client.lookupIp("8.8.8.8").ip().equals("8.8.8.8"), "lookup");
            try {
                client.account();
                check(false, "account should fail");
            } catch (ApiException e) {
                check(e.statusCode() == 403 && e.detail().equals("no scope"), "api error");
            }
            List<String> ips = new ArrayList<>();
            try (EventStream<ProxyEvent> events = client.proxies().stream()) {
                events.forEach(event -> ips.add(event.ip()));
            }
            check(ips.equals(List.of("10.0.0.1", "10.0.0.2")), "stream");
        } finally {
            server.stop(0);
        }

        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        try {
            GrpcSchema.fetch(
                    "consumer-key",
                    FetchOptions.builder().endpoint("127.0.0.1:" + port).plaintext(true).build());
            check(false, "grpc schema fetch should fail");
        } catch (SchemaException e) {
            check(e.code() == Status.Code.UNAVAILABLE, "grpc transport");
        }
        System.out.println("consumer ok: synthient-java " + SynthientClient.VERSION);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError("consumer check failed: " + what);
        }
    }

    private Main() {}
}
