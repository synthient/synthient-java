package com.synthient.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A parsed TLS ClientHello. Fields the sensor could not parse are absent, and new fields may appear.
 *
 * @param recordVersion the record layer version
 * @param handshakeVersion the handshake version
 * @param clientRandom the hex client random
 * @param sessionId the hex session ID
 * @param sessionIdLength the session ID length in bytes
 * @param sni the server name indication
 * @param cipherSuites the offered cipher suites
 * @param compressionMethods the offered compression methods
 * @param extensions the extensions in wire order
 * @param supportedGroups the supported groups
 * @param signatureAlgorithms the signature algorithms
 * @param alpn the application protocols
 * @param supportedVersions the supported TLS versions
 * @param ecPointFormats the elliptic curve point formats
 * @param keyShareGroups the key share groups
 * @param pskKeyExchangeModes the pre-shared key exchange modes
 * @param compressCertificate the certificate compression algorithms
 * @param hasGrease whether GREASE values were present
 * @param applicationSettings whether the application settings extension was present
 * @param delegatedCredentials whether the delegated credentials extension was present
 * @param encryptThenMac whether the encrypt-then-MAC extension was present
 * @param extendedMasterSecret whether the extended master secret extension was present
 * @param postHandshakeAuth whether the post-handshake authentication extension was present
 * @param renegotiationInfo whether the renegotiation info extension was present
 * @param signedCertificateTimestamps whether the signed certificate timestamps extension was present
 * @param statusRequest whether the status request extension was present
 */
public record TlsClientHello(
        @JsonProperty("record_version") @Nullable String recordVersion,
        @JsonProperty("handshake_version") @Nullable String handshakeVersion,
        @JsonProperty("client_random") @Nullable String clientRandom,
        @JsonProperty("session_id") @Nullable String sessionId,
        @JsonProperty("session_id_length") @Nullable Integer sessionIdLength,
        @JsonProperty("sni") @Nullable String sni,
        @JsonProperty("cipher_suites") @Nullable List<TlsCodeName> cipherSuites,
        @JsonProperty("compression_methods") @Nullable List<Integer> compressionMethods,
        @JsonProperty("extensions") @Nullable List<TlsExtension> extensions,
        @JsonProperty("supported_groups") @Nullable List<TlsCodeName> supportedGroups,
        @JsonProperty("signature_algorithms") @Nullable List<TlsCodeName> signatureAlgorithms,
        @JsonProperty("alpn") @Nullable List<String> alpn,
        @JsonProperty("supported_versions") @Nullable List<String> supportedVersions,
        @JsonProperty("ec_point_formats") @Nullable List<String> ecPointFormats,
        @JsonProperty("key_share_groups") @Nullable List<String> keyShareGroups,
        @JsonProperty("psk_key_exchange_modes") @Nullable List<String> pskKeyExchangeModes,
        @JsonProperty("compress_certificate") @Nullable List<String> compressCertificate,
        @JsonProperty("has_grease") @Nullable Boolean hasGrease,
        @JsonProperty("application_settings") @Nullable Boolean applicationSettings,
        @JsonProperty("delegated_credentials") @Nullable Boolean delegatedCredentials,
        @JsonProperty("encrypt_then_mac") @Nullable Boolean encryptThenMac,
        @JsonProperty("extended_master_secret") @Nullable Boolean extendedMasterSecret,
        @JsonProperty("post_handshake_auth") @Nullable Boolean postHandshakeAuth,
        @JsonProperty("renegotiation_info") @Nullable Boolean renegotiationInfo,

        @JsonProperty("signed_certificate_timestamps") @Nullable Boolean signedCertificateTimestamps,

        @JsonProperty("status_request") @Nullable Boolean statusRequest) {

    public TlsClientHello {
        cipherSuites = cipherSuites == null ? null : List.copyOf(cipherSuites);
        compressionMethods = compressionMethods == null ? null : List.copyOf(compressionMethods);
        extensions = extensions == null ? null : List.copyOf(extensions);
        supportedGroups = supportedGroups == null ? null : List.copyOf(supportedGroups);
        signatureAlgorithms = signatureAlgorithms == null ? null : List.copyOf(signatureAlgorithms);
        alpn = alpn == null ? null : List.copyOf(alpn);
        supportedVersions = supportedVersions == null ? null : List.copyOf(supportedVersions);
        ecPointFormats = ecPointFormats == null ? null : List.copyOf(ecPointFormats);
        keyShareGroups = keyShareGroups == null ? null : List.copyOf(keyShareGroups);
        pskKeyExchangeModes = pskKeyExchangeModes == null ? null : List.copyOf(pskKeyExchangeModes);
        compressCertificate = compressCertificate == null ? null : List.copyOf(compressCertificate);
    }
}
