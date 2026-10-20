package com.synthient.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * An event from the torrents feed. Every field may be absent.
 *
 * @param infoHash the hex info hash
 * @param totalSize the total size in bytes
 * @param pieceLength the piece length in bytes
 */
public record TorrentEvent(
        @JsonProperty("info_hash") @Nullable String infoHash,
        @JsonProperty("name") @Nullable String name,
        @JsonProperty("magnet_uri") @Nullable String magnetUri,
        @JsonProperty("total_size") @Nullable Long totalSize,
        @JsonProperty("piece_length") @Nullable Long pieceLength,
        @JsonProperty("file_count") @Nullable Integer fileCount,
        @JsonProperty("timestamp") @Nullable Instant timestamp,
        @JsonProperty("files") @Nullable List<TorrentFile> files,
        @JsonProperty("peers") @Nullable List<TorrentPeer> peers) {

    public TorrentEvent {
        files = files == null ? null : List.copyOf(files);
        peers = peers == null ? null : List.copyOf(peers);
    }
}
