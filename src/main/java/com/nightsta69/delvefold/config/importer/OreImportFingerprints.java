package com.nightsta69.delvefold.config.importer;

import com.nightsta69.delvefold.config.ConfigJson;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

/** Deterministic SHA-256 bindings used to reject stale ore-import sessions. */
public final class OreImportFingerprints {
    private OreImportFingerprints() {}

    /**
     * Hashes sorted block IDs and each block's sorted, deduplicated tag IDs.
     *
     * <p>Duplicate block entries are merged before hashing, null entries are ignored, and every string is UTF-8
     * length-prefixed. Therefore registry iteration order does not affect the lowercase 64-character SHA-256 result.
     *
     * @param registry registry snapshot whose blocks and tag membership should be bound to an import session
     * @return deterministic lowercase hexadecimal SHA-256 fingerprint
     */
    public static String registry(OreImportRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        TreeMap<String, TreeSet<String>> tagsByBlock = new TreeMap<>();
        if (registry.blocks() != null) {
            for (OreImportRegistry.BlockEntry block : registry.blocks()) {
                if (block == null) {
                    continue;
                }
                tagsByBlock
                        .computeIfAbsent(block.id(), ignored -> new TreeSet<>())
                        .addAll(block.tags());
            }
        }

        MessageDigest digest = sha256();
        updateInt(digest, tagsByBlock.size());
        for (var entry : tagsByBlock.entrySet()) {
            updateString(digest, entry.getKey());
            updateInt(digest, entry.getValue().size());
            for (String tag : entry.getValue()) {
                updateString(digest, tag);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Hashes the canonical Delvefold JSON form, conservatively including order and byte-level changes.
     *
     * @param profile immutable base profile to bind to an import preview
     * @return lowercase hexadecimal SHA-256 fingerprint of the UTF-8 canonical JSON bytes
     */
    public static String profile(OreProfileDocument profile) {
        Objects.requireNonNull(profile, "profile");
        byte[] json = ConfigJson.GSON.toJson(profile).getBytes(StandardCharsets.UTF_8);
        return HexFormat.of().formatHex(sha256().digest(json));
    }

    private static void updateString(MessageDigest digest, String value) {
        byte[] bytes = Objects.requireNonNull(value, "fingerprint value").getBytes(StandardCharsets.UTF_8);
        updateInt(digest, bytes.length);
        digest.update(bytes);
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
