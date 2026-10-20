package com.synthient.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * A TLS extension in a ClientHello.
 *
 * @param code the extension type
 * @param length the extension data length in bytes
 */
public record TlsExtension(
        @JsonProperty("code") @Nullable Integer code,
        @JsonProperty("name") @Nullable String name,
        @JsonProperty("length") @Nullable Integer length) {}
