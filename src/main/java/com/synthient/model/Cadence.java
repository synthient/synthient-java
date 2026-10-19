package com.synthient.model;

/** Known values of {@link ActivityStats#cadence()}. The API may add others. */
public final class Cadence {
    public static final String NO_ACTIVITY = "No activity in the last 30 days.";
    public static final String EVEN = "Activity is evenly distributed across the day.";
    public static final String WEEKDAY_BUSINESS_HOURS = "Activity follows a weekday business-hours pattern.";
    public static final String WEEKEND = "Activity follows a weekend pattern.";
    public static final String BURSTY = "Activity is burst-driven with significant peaks.";

    private Cadence() {}
}
