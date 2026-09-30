package com.synthient.model;

/** Known values of {@link DomainInfo#status()}. The API may add values, so code should accept others. */
public final class DomainStatus {
    public static final String ACTIVE = "active";
    public static final String INACTIVE = "inactive";

    private DomainStatus() {}
}
