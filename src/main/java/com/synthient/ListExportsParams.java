package com.synthient;

import org.jspecify.annotations.Nullable;

/** Options for listing a feed's exports. Build one with {@link #builder()}. */
public final class ListExportsParams {
    private final @Nullable Integer limit;
    private final @Nullable String cursor;

    private ListExportsParams(@Nullable Integer limit, @Nullable String cursor) {
        this.limit = limit;
        this.cursor = cursor;
    }

    /** Returns a builder with no limit and no cursor. */
    public static Builder builder() {
        return new Builder();
    }

    /** Returns the page size, or null for the API default of 100. */
    public @Nullable Integer limit() {
        return limit;
    }

    /** Returns the cursor to start from, or null for the first page. */
    public @Nullable String cursor() {
        return cursor;
    }

    /** Builds {@link ListExportsParams}. */
    public static final class Builder {
        private @Nullable Integer limit;
        private @Nullable String cursor;

        private Builder() {}

        /**
         * Sets the page size. The API allows up to 500 and clamps larger values.
         *
         * @throws IllegalArgumentException if the limit is less than 1
         */
        public Builder limit(int limit) {
            if (limit < 1) {
                throw new IllegalArgumentException("limit must be at least 1, got " + limit);
            }
            this.limit = limit;
            return this;
        }

        /** Sets the cursor to start from, taken from {@link com.synthient.model.ExportPage#nextCursor()}. */
        public Builder cursor(@Nullable String cursor) {
            this.cursor = cursor;
            return this;
        }

        /** Returns the options. */
        public ListExportsParams build() {
            return new ListExportsParams(limit, cursor);
        }
    }
}
