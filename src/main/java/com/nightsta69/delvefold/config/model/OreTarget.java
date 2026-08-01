package com.nightsta69.delvefold.config.model;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * Immutable exact-block or output-tag candidate, its replaceable host tag, block state, and relative weight.
 *
 * <p>Exactly one of {@code block} and {@code blockTag} must be populated after validation. State entries are sorted and
 * defensively copied so deterministic candidate ordering cannot depend on caller-owned map iteration. Weight is scoped
 * to candidates sharing the same host tag; it does not change vein count or the generation-work budget.
 *
 * @param block exact output block registry ID, or an empty string for a tag-driven target
 * @param blockTag output block-tag ID without a leading {@code #}, or an empty string for an exact target
 * @param state immutable sorted block-state property/value assignments; empty means the block's default state
 * @param replaceTag host block-tag registry ID identifying positions this output may replace
 * @param weight relative host-group weight from {@value #MIN_WEIGHT} through {@value #MAX_WEIGHT}; omitted schema-2
 *     values normalize to {@value #DEFAULT_WEIGHT}
 */
public record OreTarget(String block, String blockTag, Map<String, String> state, String replaceTag, Integer weight) {
    /** Compatibility weight used when an older schema-2 target omits the additive weight field. */
    public static final int DEFAULT_WEIGHT = 1;
    /** Minimum accepted relative output weight. */
    public static final int MIN_WEIGHT = 1;
    /** Maximum accepted relative output weight. */
    public static final int MAX_WEIGHT = 1000;

    /**
     * Normalizes source identifiers and takes a sorted immutable snapshot of block-state properties.
     *
     * <p>This constructor applies compatibility defaults but deliberately leaves source exclusivity, resource-ID,
     * property, registry, and weight-range checks to full-profile validation so failures retain exact JSON paths.
     *
     * @param block exact output block registry ID, or {@code null} for an empty exact-block source
     * @param blockTag output block-tag ID, optionally prefixed with {@code #}, or {@code null} for an empty tag source
     * @param state block-state property/value assignments, or {@code null} for an empty state
     * @param replaceTag host block-tag registry ID, or {@code null} for an empty host tag
     * @param weight relative output weight, or {@code null} for {@value #DEFAULT_WEIGHT}
     */
    public OreTarget(
            @Nullable String block,
            @Nullable String blockTag,
            @Nullable Map<String, String> state,
            @Nullable String replaceTag,
            @Nullable Integer weight) {
        this.block = block == null ? "" : block.trim();
        this.blockTag = blockTag == null ? "" : stripHash(blockTag.trim());
        this.state = state == null || state.isEmpty() ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(state));
        this.replaceTag = replaceTag == null ? "" : replaceTag.trim();
        this.weight = weight == null ? DEFAULT_WEIGHT : weight;
    }

    /**
     * Creates a target at the compatibility weight used before weighted outputs were added to schema 2.
     *
     * @param block exact output block registry ID
     * @param blockTag output block-tag ID, optionally prefixed with {@code #}
     * @param state block-state property/value assignments
     * @param replaceTag host block-tag registry ID
     */
    public OreTarget(String block, String blockTag, Map<String, String> state, String replaceTag) {
        this(block, blockTag, state, replaceTag, DEFAULT_WEIGHT);
    }

    /**
     * Creates an exact-block target at the compatibility weight.
     *
     * @param block exact output block registry ID
     * @param replaceTag host block-tag registry ID
     * @return exact-block target with an empty state and weight {@value #DEFAULT_WEIGHT}
     */
    public static OreTarget of(String block, String replaceTag) {
        return of(block, replaceTag, DEFAULT_WEIGHT);
    }

    /**
     * Creates a weighted exact-block target using the block's default state.
     *
     * @param block exact output block registry ID
     * @param replaceTag host block-tag registry ID
     * @param weight relative host-group weight, validated later in the range 1 through 1000
     * @return weighted exact-block target
     */
    public static OreTarget of(String block, String replaceTag, int weight) {
        return new OreTarget(block, "", Map.of(), replaceTag, weight);
    }

    /**
     * Creates an output-tag target at the compatibility weight.
     *
     * @param blockTag output block-tag ID, optionally prefixed with {@code #}
     * @param replaceTag host block-tag registry ID
     * @return tag-driven target with an empty state and weight {@value #DEFAULT_WEIGHT}
     */
    public static OreTarget ofTag(String blockTag, String replaceTag) {
        return ofTag(blockTag, replaceTag, DEFAULT_WEIGHT);
    }

    /**
     * Creates a weighted output-tag target using each resolved member's default state.
     *
     * <p>Registry members are sorted by ID and share this total weight equally. Missing optional tags warn and skip;
     * missing required tags reject the containing profile.
     *
     * @param blockTag output block-tag ID, optionally prefixed with {@code #}
     * @param replaceTag host block-tag registry ID
     * @param weight total relative host-group weight, validated later in the range 1 through 1000
     * @return weighted tag-driven target
     */
    public static OreTarget ofTag(String blockTag, String replaceTag, int weight) {
        return new OreTarget("", blockTag, Map.of(), replaceTag, weight);
    }

    /**
     * Copies this target with a replacement relative weight.
     *
     * @param replacement replacement weight, validated with the complete profile before publication
     * @return immutable target copy retaining source, state, and host tag
     */
    public OreTarget withWeight(int replacement) {
        return new OreTarget(block, blockTag, state, replaceTag, replacement);
    }

    /**
     * Reports whether this target expands an output block tag rather than naming one exact block.
     *
     * @return {@code true} when {@link #blockTag()} is nonblank
     */
    public boolean tagDriven() {
        return !blockTag.isBlank();
    }

    /**
     * Returns the source ID in user-facing syntax.
     *
     * @return exact block ID, or a tag ID prefixed with {@code #}
     */
    public String sourceId() {
        return tagDriven() ? '#' + blockTag : block;
    }

    private static String stripHash(String value) {
        return value.startsWith("#") ? value.substring(1) : value;
    }
}
