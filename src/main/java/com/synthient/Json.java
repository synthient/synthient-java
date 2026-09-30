package com.synthient;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.type.LogicalType;

/** The JSON mapper shared by every client. It is immutable and safe for concurrent use. */
final class Json {
    /**
     * Rejects values of the wrong JSON type, such as {@code "15169"} for a number or {@code 15169} for a
     * string, and reads timestamps with {@link UnixTimeDeserializer}.
     */
    static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .withCoercionConfig(
                    LogicalType.Textual,
                    config -> config.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                            .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                            .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
            .addModule(new SimpleModule().addDeserializer(Instant.class, new UnixTimeDeserializer()))
            .build();

    private Json() {}

    /**
     * Decodes a JSON object into a model.
     *
     * @throws SynthientException if the body is not a valid instance of the model
     */
    static <T> T decode(byte[] body, Class<T> type) {
        T value;
        try {
            value = MAPPER.readValue(body, type);
        } catch (JacksonException e) {
            throw new SynthientException("invalid " + type.getSimpleName() + " in response", e);
        }
        if (value == null) {
            throw new SynthientException("invalid " + type.getSimpleName() + " in response: null");
        }
        return value;
    }

    /**
     * Reads Unix timestamps and RFC 3339 strings. Numbers of at least 10^12 are milliseconds, which the
     * anonymizer and Helios streams send; smaller numbers are seconds.
     */
    static final class UnixTimeDeserializer extends ValueDeserializer<Instant> {
        private static final long MILLISECOND_THRESHOLD = 1_000_000_000_000L;

        @Override
        public Instant deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
                long value = parser.getLongValue();
                return value >= MILLISECOND_THRESHOLD ? Instant.ofEpochMilli(value) : Instant.ofEpochSecond(value);
            }
            if (parser.hasToken(JsonToken.VALUE_STRING)) {
                String text = parser.getString();
                try {
                    return OffsetDateTime.parse(text).toInstant();
                } catch (DateTimeParseException e) {
                    return (Instant) context.handleWeirdStringValue(Instant.class, text, "not an RFC 3339 timestamp");
                }
            }
            return (Instant) context.handleUnexpectedToken(Instant.class, parser);
        }
    }
}
