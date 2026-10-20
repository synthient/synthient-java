package com.synthient.grpcschema;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class FetchOptions {
    private final String endpoint;
    private final boolean plaintext;
    private final List<String> symbols;
    private final Duration timeout;

    private FetchOptions(Builder builder) {
        endpoint = builder.endpoint;
        plaintext = builder.plaintext;
        symbols = List.copyOf(builder.symbols);
        timeout = builder.timeout;
    }

    /** Returns a builder that fetches every service from {@link GrpcSchema#DEFAULT_ENDPOINT} over TLS. */
    public static Builder builder() {
        return new Builder();
    }

    /** Returns the endpoint, or an empty string for {@link GrpcSchema#DEFAULT_ENDPOINT}. */
    public String endpoint() {
        return endpoint;
    }

    public boolean plaintext() {
        return plaintext;
    }

    /** Returns the symbols to resolve, or an empty list for every listed service. */
    public List<String> symbols() {
        return symbols;
    }

    /** Returns the deadline for the whole fetch. */
    public Duration timeout() {
        return timeout;
    }

    public static final class Builder {
        private String endpoint = "";
        private boolean plaintext;
        private final Set<String> symbols = new LinkedHashSet<>();
        private Duration timeout = Duration.ofSeconds(30);

        private Builder() {}

        /**
         * Sets the server: {@code host:port}, a bare host with port 443 assumed, or a {@code grpc://} or {@code
         * https://} URL.
         */
        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        /** Disables TLS, for local development servers. */
        public Builder plaintext(boolean plaintext) {
            this.plaintext = plaintext;
            return this;
        }

        /**
         * Adds fully qualified service or message names to resolve, such as {@code synthient.v1.SynthientService}.
         * Blank and repeated names are ignored. Without any, every service the server lists is resolved except
         * the reflection service itself, which can be named here instead.
         */
        public Builder symbols(String... symbols) {
            for (String symbol : symbols) {
                if (!symbol.isBlank()) {
                    this.symbols.add(symbol.strip());
                }
            }
            return this;
        }

        /**
         * Sets the deadline for the whole fetch, by default 30 seconds.
         *
         * @throws IllegalArgumentException if the timeout is not positive
         */
        public Builder timeout(Duration timeout) {
            if (timeout.isNegative() || timeout.isZero()) {
                throw new IllegalArgumentException("timeout must be positive, got " + timeout);
            }
            this.timeout = timeout;
            return this;
        }

        public FetchOptions build() {
            return new FetchOptions(this);
        }
    }
}
