package com.synthient.model;

/** Known values of {@link Export#variant()} and {@link ExportMeta#variant()}, and the variants a feed can ask for. */
public final class ExportVariant {
    /** Every sighting. Exports are full unless lite is asked for. */
    public static final String FULL = "full";
    /**
     * The export deduplicated to the latest row per key, such as one row per address and provider for proxies,
     * with the same columns.
     */
    public static final String LITE = "lite";

    private ExportVariant() {}
}
