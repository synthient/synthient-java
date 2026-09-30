package com.synthient.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * A TLS code point and its name. GREASE values are named like {@code GREASE(0x0a0a)}.
 *
 * @param code the code point
 * @param name the name
 */
public record TlsCodeName(
        @JsonProperty("code") @Nullable Integer code,
        @JsonProperty("name") @Nullable String name) {}
