package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * The rows and bytes a feed or dataset exported per UTC day.
 *
 * @param stream the feed's stream name
 * @param days the days, oldest first. Today is included and still growing, and days without exports are absent.
 */
public record ExportVolume(
        @JsonProperty("stream") String stream,
        @JsonProperty("days") List<ExportVolumeDay> days) {

    public ExportVolume {
        requireNonNull(stream, "stream");
        days = List.copyOf(requireNonNull(days, "days"));
    }
}
