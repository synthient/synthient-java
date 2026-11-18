package com.synthient.model;

/**
 * Known values of the {@code transport} of {@link HeliosDnsEvent}, {@link HeliosHttpsEvent}, and {@link
 * TlsClientHello}. The API may add others.
 */
public final class HeliosTransport {
    public static final String TCP = "tcp";
    public static final String UDP = "udp";
    public static final String QUIC = "quic";

    private HeliosTransport() {}
}
