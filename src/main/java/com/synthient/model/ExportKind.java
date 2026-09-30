package com.synthient.model;

/** Known values of {@link Export#kind()} and {@link ExportMeta#kind()}. The API may add values, so code should accept others. */
public final class ExportKind {
    public static final String HOURLY = "hourly";
    public static final String DAILY = "daily";

    private ExportKind() {}
}
