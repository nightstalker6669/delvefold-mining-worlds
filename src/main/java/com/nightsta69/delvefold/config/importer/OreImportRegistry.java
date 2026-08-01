package com.nightsta69.delvefold.config.importer;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Deterministic registry snapshot boundary so discovery and planning remain unit-testable. */
public interface OreImportRegistry {
    /** Every installed block, sorted by registry ID. */
    List<BlockEntry> blocks();

    /** Installed members of a block tag, sorted by registry ID. Missing tags return an empty list. */
    List<String> tagMembers(String tagId);

    record BlockEntry(String id, Set<String> tags) {
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
