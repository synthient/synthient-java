package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One page of a feed's exports, newest first.
 *
 * @param stream the feed's stream name
 * @param exports the exports on this page, sent as {@code feeds}
 * @param nextCursor the cursor of the next page, or null on the last page
 */
public record ExportPage(
        @JsonProperty(value = "stream", required = true) String stream,
        @JsonProperty(value = "feeds", required = true) List<Export> exports,
        @JsonProperty("next_cursor") @Nullable String nextCursor) {

    public ExportPage {
        requireNonNull(stream, "stream");
        exports = List.copyOf(requireNonNull(exports, "exports"));
        if (nextCursor != null && nextCursor.isEmpty()) {
            nextCursor = null;
        }
    }
}
