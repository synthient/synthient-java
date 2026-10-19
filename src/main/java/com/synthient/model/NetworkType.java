package com.synthient.model;

/** Known values of {@link Network#type()}. The API may add others. */
public final class NetworkType {
    public static final String MOBILE = "MOBILE";
    public static final String SATELLITE = "SATELLITE";
    public static final String IN_FLIGHT_WIFI = "IN_FLIGHT_WIFI";
    public static final String RESIDENTIAL = "RESIDENTIAL";
    public static final String CORPORATE = "CORPORATE";
    public static final String ACADEMIC = "ACADEMIC";
    public static final String DATACENTER = "DATACENTER";
    public static final String GOVERNMENT = "GOVERNMENT";

    private NetworkType() {}
}
