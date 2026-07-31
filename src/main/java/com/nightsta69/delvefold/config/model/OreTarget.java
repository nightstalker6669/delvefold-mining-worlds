package com.nightsta69.delvefold.config.model;

import java.util.Map;
import java.util.Collections;
import java.util.TreeMap;

/** A block state to place and the host-block tag it may replace. */
public record OreTarget(String block, Map<String, String> state, String replaceTag) {
    public OreTarget {
        block = block == null ? "" : block.trim();
        state = state == null || state.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new TreeMap<>(state));
        replaceTag = replaceTag == null ? "" : replaceTag.trim();
    }

    public static OreTarget of(String block, String replaceTag) {
        return new OreTarget(block, Map.of(), replaceTag);
    }
}
