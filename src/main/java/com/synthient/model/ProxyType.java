package com.synthient.model;

/** Known values of the {@code type} of {@link ProxyEvent}, {@link AnonymizerEvent}, and {@link Ja4tEvent}. The API may add values, so code should accept others. */
public final class ProxyType {
    public static final String FREE_VPN = "FREE_VPN";
    public static final String COMMERCIAL_VPN = "COMMERCIAL_VPN";
    public static final String ENTERPRISE_VPN = "ENTERPRISE_VPN";
    public static final String MOBILE_PROXY = "MOBILE_PROXY";
    public static final String BLOCKCHAIN_PROXY = "BLOCKCHAIN_PROXY";
    public static final String RESIDENTIAL_PROXY = "RESIDENTIAL_PROXY";
    public static final String PUBLIC_PROXY = "PUBLIC_PROXY";
    public static final String DATACENTER_PROXY = "DATACENTER_PROXY";
    public static final String TOR_NODE = "TOR_NODE";
    public static final String PRIVATE_RELAY = "PRIVATE_RELAY";
    public static final String BOTNET = "BOTNET";
    public static final String SEARCH_ENGINE = "SEARCH_ENGINE";
    public static final String AI_CRAWLER = "AI_CRAWLER";
    public static final String SOCIAL_MEDIA = "SOCIAL_MEDIA";
    public static final String UPTIME_MONITOR = "UPTIME_MONITOR";
    public static final String LINK_PREVIEW = "LINK_PREVIEW";
    public static final String SEO_CRAWLER = "SEO_CRAWLER";
    public static final String WEB_ARCHIVER = "WEB_ARCHIVER";
    public static final String WEBHOOK_PROVIDER = "WEBHOOK_PROVIDER";
    public static final String PAYMENT_PROCESSOR = "PAYMENT_PROCESSOR";
    public static final String INTERNET_SCANNER = "INTERNET_SCANNER";

    private ProxyType() {}
}
