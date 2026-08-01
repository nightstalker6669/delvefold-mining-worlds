package com.nightsta69.delvefold.config.model;

import java.util.List;

/**
 * Immutable schema-2 ore-profile document with an optimistic-concurrency revision.
 *
 * <p>The ordered rules are defensively copied. Publishing a new profile changes placement only in chunks generated
 * afterward; existing chunk contents are never silently regenerated.
 *
 * @param schemaVersion serialized schema version, currently {@value #CURRENT_SCHEMA_VERSION}
 * @param revision nonnegative optimistic-concurrency revision incremented by accepted profile mutations
 * @param profile stable descriptive profile ID
 * @param rules immutable ordered snapshot containing at most 512 ore rules after validation
 */
public record OreProfileDocument(int schemaVersion, long revision, String profile, List<OreRule> rules) {
    /** Stable configuration schema supported throughout the Delvefold 1.x series. */
    public static final int CURRENT_SCHEMA_VERSION = 2;

    /**
     * Creates a profile snapshot and defensively copies its rule list.
     *
     * <p>Malformed deserialization input with a blank profile is normalized to {@code custom}; a missing rule list is
     * normalized to an empty immutable list. Range, uniqueness, registry, and safety-budget validation is performed by
     * the configuration service before publication rather than by this value constructor.
     *
     * @param schemaVersion serialized schema version
     * @param revision optimistic-concurrency revision
     * @param profile descriptive profile ID
     * @param rules ordered ore rules
     */
    public OreProfileDocument {
        profile = profile == null || profile.isBlank() ? "custom" : profile.trim();
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    /**
     * Produces a replacement snapshot at the next optimistic-concurrency revision.
     *
     * @param replacementRules complete ordered replacement rule list
     * @param replacementProfile replacement descriptive profile ID
     * @return new schema-2 snapshot whose revision is this revision plus one
     */
    public OreProfileDocument nextRevision(List<OreRule> replacementRules, String replacementProfile) {
        return new OreProfileDocument(CURRENT_SCHEMA_VERSION, revision + 1, replacementProfile, replacementRules);
    }
}
