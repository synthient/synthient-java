package com.synthient;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

final class Json {
    /**
     * Rejects values of the wrong JSON type, such as {@code "15169"} for a number or {@code 15169} for a
     * string.
     */
    static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .withCoercionConfig(
                    LogicalType.Textual,
                    config -> config.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                            .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                            .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
            .build();

    private Json() {}

    /** @throws SynthientException if the body is not a valid instance of the model */
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
}
