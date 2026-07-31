package com.nightsta69.delvefold.config.model;

import java.util.Map;
import java.util.Collections;
import java.util.TreeMap;

/** An exact block or output block tag, plus the host-block tag it may replace. */
public record OreTarget(String block, String blockTag, Map<String, String> state, String replaceTag) {
    public OreTarget {
        block = block == null ? "" : block.trim();
        blockTag = blockTag == null ? "" : stripHash(blockTag.trim());
        state = state == null || state.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new TreeMap<>(state));
        replaceTag = replaceTag == null ? "" : replaceTag.trim();
    }

    public static OreTarget of(String block, String replaceTag) {
        return new OreTarget(block, "", Map.of(), replaceTag);
    }

    public static OreTarget ofTag(String blockTag, String replaceTag) {
        return new OreTarget("", blockTag, Map.of(), replaceTag);
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
