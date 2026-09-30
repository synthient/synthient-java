package com.synthient.model;

/** Known values of {@link ParquetColumn#type()}. The API may add values, so code should accept others. */
public final class ParquetType {
    public static final String STRING = "string";
    public static final String INT64 = "int64";
    public static final String UINT32 = "uint32";
    public static final String UINT64 = "uint64";
    public static final String BOOL = "bool";
    public static final String BYTES = "bytes";

    private ParquetType() {}
}
