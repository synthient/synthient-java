package com.synthient.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * A file in a torrent.
 *
 * @param path the file's path in the torrent
 * @param length the file size in bytes
 */
public record TorrentFile(
        @JsonProperty("path") @Nullable String path,
        @JsonProperty("length") @Nullable Long length) {}
