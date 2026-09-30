package com.synthient;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNullElse;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import okhttp3.Headers;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.MissingNode;

/**
 * The API answered with an error status. The fields come from the problem details document in the
 * response body when there is one.
 *
 * <p>{@link #getMessage()} holds only the status, such as {@code 403 Forbidden}, so the exception is safe
 * to log. The server's explanation is in {@link #detail()}.
 */
public class ApiException extends SynthientException {
    private static final long serialVersionUID = 1L;

    private final int statusCode;
    private final String type;
    private final String title;
    private final String detail;

    @SuppressWarnings("serial")
    private final Map<String, List<String>> errors;

    private final String requestId;
    private final @Nullable Duration retryAfter;

    /**
     * Creates an API exception, for example to stub the SDK in tests.
     *
     * @param statusCode the HTTP status
     * @param type the problem type URI, or empty
     * @param title the problem title, or empty
     * @param detail the problem detail, or empty
     * @param errors validation errors by field, or empty
     * @param requestId the request ID, or empty
     * @param retryAfter the server's Retry-After delay, or null
     * @param cause the failure that interrupted reading the error body, or null
     */
    public ApiException(
            int statusCode,
            String type,
            String title,
            String detail,
            Map<String, List<String>> errors,
            String requestId,
            @Nullable Duration retryAfter,
            @Nullable Throwable cause) {
        super(message(statusCode), cause);
        this.statusCode = statusCode;
        this.type = type;
        this.title = title;
        this.detail = detail;
        this.errors = errors.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        this.requestId = requestId;
        this.retryAfter = retryAfter;
    }

    static ApiException fromResponse(int statusCode, Headers headers, byte[] body, @Nullable Throwable readFailure) {
        JsonNode problem = parseObject(body);
        String requestId = headers.get("X-Request-Id");
        if (requestId == null || requestId.isEmpty()) {
            requestId = problem.path("instance").asString("");
        }
        String contentType = requireNonNullElse(headers.get("Content-Type"), "");
        boolean plainText = contentType.strip().toLowerCase(Locale.ROOT).matches("text/plain\\s*(;.*)?");
        String detail = !problem.isObject() && plainText
                ? new String(body, UTF_8).strip()
                : problem.path("detail").asString("");
        return new ApiException(
                statusCode,
                problem.path("type").asString(""),
                problem.path("title").asString(""),
                detail,
                parseErrors(problem.get("errors")),
                requestId,
                Transport.retryAfter(headers),
                readFailure);
    }

    /** Parses a JSON object, returning a missing node for anything else. */
    private static JsonNode parseObject(byte[] body) {
        try {
            JsonNode node = Json.MAPPER.readTree(body);
            return node.isObject() ? node : MissingNode.getInstance();
        } catch (JacksonException e) {
            return MissingNode.getInstance();
        }
    }

    private static Map<String, List<String>> parseErrors(@Nullable JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Map<String, List<String>> errors = new HashMap<>();
        for (Map.Entry<String, JsonNode> field : node.properties()) {
            if (!field.getValue().isArray()) {
                return Map.of();
            }
            List<String> messages = new ArrayList<>();
            for (JsonNode message : field.getValue()) {
                if (!message.isString()) {
                    return Map.of();
                }
                messages.add(message.asString());
            }
            errors.put(field.getKey(), messages);
        }
        return errors;
    }

    private static String message(int statusCode) {
        String reason = switch (statusCode) {
            case 301 -> "Moved Permanently";
            case 302 -> "Found";
            case 307 -> "Temporary Redirect";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 402 -> "Payment Required";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 408 -> "Request Timeout";
            case 409 -> "Conflict";
            case 413 -> "Content Too Large";
            case 422 -> "Unprocessable Content";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            case 504 -> "Gateway Timeout";
            default -> "";
        };
        return reason.isEmpty() ? String.valueOf(statusCode) : statusCode + " " + reason;
    }

    /** Returns the HTTP status. */
    public int statusCode() {
        return statusCode;
    }

    /** Returns the problem type URI, or an empty string. */
    public String type() {
        return type;
    }

    /** Returns the short problem summary, or an empty string. */
    public String title() {
        return title;
    }

    /**
     * Returns the server's explanation of the problem, or an empty string. For a plain-text error it is
     * the whole body.
     */
    public String detail() {
        return detail;
    }

    /** Returns validation errors by field name, or an empty map. */
    public Map<String, List<String>> errors() {
        return errors;
    }

    /** Returns the {@code X-Request-Id} of the response, or an empty string. Quote it when contacting support. */
    public String requestId() {
        return requestId;
    }

    /**
     * Returns how long the server asked the client to wait before retrying, or null when it did not say. A
     * 429 without it means the monthly budget is spent or too many streams are open; retrying will not help.
     */
    public @Nullable Duration retryAfter() {
        return retryAfter;
    }
}
