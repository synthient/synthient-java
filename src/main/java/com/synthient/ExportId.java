package com.synthient;

import java.time.LocalDate;

/**
 * Builds the IDs of feed exports: {@link #LATEST}, a UTC day such as {@code 2026-05-03}, or an hour of a day
 * such as {@code 2026-05-03/7}. {@link com.synthient.model.Export#id()} returns ready-made IDs.
 *
 * <p>An hourly export stays available until its day is rolled up into the daily export, shortly after
 * midnight UTC. After that the API answers 400 for the hour. While a day is being rolled up, the daily
 * export answers 404 with a {@link ApiException#retryAfter() Retry-After}.
 */
public final class ExportId {
    /** The newest export: the latest hour during the day, then the daily rollup. */
    public static final String LATEST = "latest";

    private ExportId() {}

    /** Returns the ID of the daily export of a UTC day. */
    public static String daily(LocalDate date) {
        return date.toString();
    }

    /**
     * Returns the ID of the hourly export of an hour of a UTC day.
     *
     * @throws IllegalArgumentException if the hour is not between 0 and 23
     */
    public static String hourly(LocalDate date, int hour) {
        if (hour < 0 || hour > 23) {
            throw new IllegalArgumentException("hour must be between 0 and 23, got " + hour);
        }
        return date + "/" + hour;
    }
}
