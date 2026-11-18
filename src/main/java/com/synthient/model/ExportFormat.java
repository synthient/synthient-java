package com.synthient.model;

/**
 * Known values of {@link ExportMeta#format()} and {@link ExportFile#format()}, and the formats a dataset download
 * can ask for. The API may add others.
 */
public final class ExportFormat {
    public static final String PARQUET = "parquet";
    /** Gzip-compressed CSV with a header row, served as {@code .csv.gz}. */
    public static final String CSV = "csv";
    /** A MaxMind DB file. */
    public static final String MMDB = "mmdb";
    /** The WHOIS dataset's archive of one Parquet file per entity. */
    public static final String TAR_GZ = "tar.gz";

    private ExportFormat() {}
}
