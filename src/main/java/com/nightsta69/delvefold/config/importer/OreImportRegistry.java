package com.nightsta69.delvefold.config.importer;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Deterministic registry snapshot boundary so discovery and planning remain unit-testable. */
public interface OreImportRegistry {
    /**
     * Returns every installed block in deterministic registry-ID order.
     *
     * @return immutable or read-only block entries sorted by registry ID
     */
    List<BlockEntry> blocks();

    /**
     * Returns installed members of a block tag in deterministic registry-ID order.
     *
     * @param tagId block-tag resource ID, with or without a leading {@code #}
     * @return immutable or read-only sorted members, or an empty list when the tag is absent
     */
    List<String> tagMembers(String tagId);

    /**
     * One installed block and the normalized block tags that contain it.
     *
     * @param id non-empty block registry ID
     * @param tags normalized, deduplicated block-tag IDs; null entries and leading {@code #} markers are removed
     */
    record BlockEntry(String id, Set<String> tags) {
        /**
         * Trims the block ID and stores a normalized, deduplicated immutable tag set.
         *
         * @param id non-empty block registry ID
         * @param tags block-tag IDs, or {@code null} for none
         */
        public BlockEntry {
            id = id == null ? "" : id.trim();
            if (id.isEmpty()) {
                throw new IllegalArgumentException("Block registry ID cannot be empty");
            }
            TreeSet<String> normalized = new TreeSet<>();
            if (tags != null) {
                tags.stream()
                        .filter(java.util.Objects::nonNull)
                        .map(String::trim)
                        .map(tag -> tag.startsWith("#") ? tag.substring(1) : tag)
                        .filter(tag -> !tag.isEmpty())
                        .forEach(normalized::add);
            }
            tags = Set.copyOf(normalized);
        }
    }
}
