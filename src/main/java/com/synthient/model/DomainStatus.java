package com.synthient.model;

/**
 * Known values of {@link DomainInfo#status()}. The API may add others.
 *
 * @deprecated Domain lookup was removed from the Synthient API and will stop working when the endpoint is turned
 *     off. It will be removed in the next major version.
 */
@Deprecated
public final class DomainStatus {
    public static final String ACTIVE = "active";
    public static final String INACTIVE = "inactive";

    private DomainStatus() {}
}
