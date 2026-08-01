package com.nightsta69.delvefold.config.model;

import java.util.Map;
import java.util.Collections;
import java.util.TreeMap;

/** An exact block or output block tag, plus its host tag and relative selection weight. */
public record OreTarget(
        String block,
        String blockTag,
        Map<String, String> state,
        String replaceTag,
        Integer weight) {
    public static final int DEFAULT_WEIGHT = 1;
    public static final int MIN_WEIGHT = 1;
    public static final int MAX_WEIGHT = 1000;

    public OreTarget {
        block = block == null ? "" : block.trim();
        blockTag = blockTag == null ? "" : stripHash(blockTag.trim());
        state = state == null || state.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new TreeMap<>(state));
        replaceTag = replaceTag == null ? "" : replaceTag.trim();
        weight = weight == null ? DEFAULT_WEIGHT : weight;
    }

    /** Source- and binary-compatible constructor for schema-2 targets predating weights. */
    public OreTarget(String block, String blockTag, Map<String, String> state, String replaceTag) {
        this(block, blockTag, state, replaceTag, DEFAULT_WEIGHT);
    }

    public static OreTarget of(String block, String replaceTag) {
        return of(block, replaceTag, DEFAULT_WEIGHT);
    }

    public static OreTarget of(String block, String replaceTag, int weight) {
        return new OreTarget(block, "", Map.of(), replaceTag, weight);
    }

    public static OreTarget ofTag(String blockTag, String replaceTag) {
        return ofTag(blockTag, replaceTag, DEFAULT_WEIGHT);
    }

    public static OreTarget ofTag(String blockTag, String replaceTag, int weight) {
        return new OreTarget("", blockTag, Map.of(), replaceTag, weight);
    }

    public OreTarget withWeight(int replacement) {
        return new OreTarget(block, blockTag, state, replaceTag, replacement);
    }

    public boolean tagDriven() {
        return !blockTag.isBlank();
    }

    public String sourceId() {
        return tagDriven() ? '#' + blockTag : block;
    }

    private static String stripHash(String value) {
        return value.startsWith("#") ? value.substring(1) : value;
    }
}
