package com.synthient.model;

/** Known values of {@link Organization#relation()}. The API may add values, so code should accept others. */
public final class Relation {
    public static final String OWNER = "OWNER";
    public static final String ADMIN = "ADMIN";
    public static final String MEMBER = "MEMBER";

    private Relation() {}
}
