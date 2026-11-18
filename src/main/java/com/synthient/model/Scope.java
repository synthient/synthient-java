package com.synthient.model;

/** Known values of {@link Account#scopes()}. The API may add others. */
public final class Scope {
    public static final String BASIC = "BASIC";
    public static final String PROXY_FEEDS = "PROXY_FEEDS";
    public static final String PROXY_FIREHOSE = "PROXY_FIREHOSE";
    public static final String ANONYMIZERS_FEED = "ANONYMIZERS_FEED";
    public static final String ANONYMIZERS_STREAM = "ANONYMIZERS_STREAM";
    public static final String JA3_FEED = "JA3_FEED";
    public static final String JA3_STREAM = "JA3_STREAM";
    public static final String TORRENTS_FEED = "TORRENTS_FEED";
    public static final String TORRENTS_STREAM = "TORRENTS_STREAM";
    public static final String HONEYPOT_HTTP_FEED = "HONEYPOT_HTTP_FEED";
    public static final String HONEYPOT_HTTP_STREAM = "HONEYPOT_HTTP_STREAM";
    public static final String HONEYPOT_HTTPS_FEED = "HONEYPOT_HTTPS_FEED";
    public static final String HONEYPOT_HTTPS_STREAM = "HONEYPOT_HTTPS_STREAM";
    public static final String HONEYPOT_ADB_FEED = "HONEYPOT_ADB_FEED";
    public static final String HONEYPOT_ADB_STREAM = "HONEYPOT_ADB_STREAM";
    public static final String HONEYPOT_DNS_FEED = "HONEYPOT_DNS_FEED";
    public static final String HONEYPOT_DNS_STREAM = "HONEYPOT_DNS_STREAM";
    public static final String JA4_FEED = "JA4_FEED";
    public static final String JA4_STREAM = "JA4_STREAM";
    public static final String JA4T_FEED = "JA4T_FEED";
    public static final String JA4T_STREAM = "JA4T_STREAM";
    /** A dashboard permission that authorizes no API operation. */
    public static final String HELIOS = "HELIOS";

    public static final String GEOLOCATION_FEED = "GEOLOCATION_FEED";
    public static final String WHOIS_FEED = "WHOIS_FEED";
    /** Reserved for an upcoming WHOIS stream. No operation accepts it yet. */
    public static final String WHOIS_STREAM = "WHOIS_STREAM";
    /** Reserved for an upcoming network IOC feed. No operation accepts it yet. */
    public static final String NETWORK_IOCS_FEED = "NETWORK_IOCS_FEED";
    /** Reserved for an upcoming network IOC feed. No operation accepts it yet. */
    public static final String NETWORK_IOCS_STREAM = "NETWORK_IOCS_STREAM";
    /** Reserved for an upcoming file IOC feed. No operation accepts it yet. */
    public static final String FILE_IOCS_FEED = "FILE_IOCS_FEED";
    /** Reserved for an upcoming file IOC feed. No operation accepts it yet. */
    public static final String FILE_IOCS_STREAM = "FILE_IOCS_STREAM";

    /**
     * @deprecated The API no longer lists this scope, which never authorized an API operation. It will be removed in
     *     the next major version.
     */
    @Deprecated
    public static final String ADMIN_PROXIES = "ADMIN_PROXIES";

    private Scope() {}
}
