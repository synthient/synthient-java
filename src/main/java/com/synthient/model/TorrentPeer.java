package com.synthient.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * A peer seen sharing a torrent.
 *
 * @param ip the peer's address
 * @param port the peer's port
 * @param encrypted whether the peer used an encrypted connection
 * @param source where the peer was found: DHT, PEX, or a tracker
 */
public record TorrentPeer(
        @JsonProperty("ip") @Nullable String ip,
        @JsonProperty("port") @Nullable Integer port,
        @JsonProperty("encrypted") @Nullable Boolean encrypted,
        @JsonProperty("source") @Nullable String source) {}
