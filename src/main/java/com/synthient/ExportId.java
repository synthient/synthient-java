package com.synthient;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonValue;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Identifies a feed export: {@link #LATEST}, a UTC day such as {@code 2026-05-03}, or an hour of a day
 * such as {@code 2026-05-03/7}. {@link com.synthient.model.Export#id()} returns ready-made IDs.
 *
 * <p>An hourly export stays available until its day is rolled up into the daily export, shortly after
 * midnight UTC. After that the API answers 400 for the hour. While a day is being rolled up, the daily
 * export answers 404 with a {@link ApiException#retryAfter() Retry-After}.
 *
 * <p>The value is checked when it is used, and an invalid one fails with {@link IllegalArgumentException}
 * before any request is sent.
 *
 * @param value the ID as the API writes it
 */
public record ExportId(@JsonValue String value) {
    /** The newest export: the latest hour during the day, then the daily rollup. */
    public static final ExportId LATEST = new ExportId("latest");

    private static final Pattern PATTERN = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})(?:/(\\d{1,2}))?");

    public ExportId {
        requireNonNull(value, "value");
    }

    /** Returns the export with the given ID. */
    public static ExportId of(String value) {
        return new ExportId(value);
    }

    /** Returns the daily export of a UTC day. */
    public static ExportId daily(LocalDate date) {
        return new ExportId(date.toString());
    }

    /**
     * Returns the hourly export of an hour of a UTC day.
     *
     * @throws IllegalArgumentException if the hour is not between 0 and 23
     */
    public static ExportId hourly(LocalDate date, int hour) {
        if (hour < 0 || hour > 23) {
            throw new IllegalArgumentException("hour must be between 0 and 23, got " + hour);
        }
        return new ExportId(date + "/" + hour);
    }

    /** Returns the path segments of the export, with the hour unpadded, or throws if the ID is invalid. */
    String toPath() {
        if (value.equals("latest")) {
            return value;
        }
        Matcher matcher = PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw invalid();
        }
        LocalDate date;
        try {
            date = LocalDate.parse(matcher.group(1));
        } catch (DateTimeParseException e) {
            throw invalid();
        }
        String hour = matcher.group(2);
        if (hour == null) {
            return date.toString();
        }
        int parsed = Integer.parseInt(hour);
        if (parsed > 23) {
            throw invalid();
        }
        return date + "/" + parsed;
    }

    private IllegalArgumentException invalid() {
        return new IllegalArgumentException("invalid export ID \"" + value + "\"");
    }

    @Override
    public String toString() {
        return value;
    }
}
