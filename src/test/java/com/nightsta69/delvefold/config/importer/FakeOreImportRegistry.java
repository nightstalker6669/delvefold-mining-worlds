package com.nightsta69.delvefold.config.importer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

final class FakeOreImportRegistry implements OreImportRegistry {
    private final List<BlockEntry> blocks;
    private final Map<String, List<String>> tagMembers;

    FakeOreImportRegistry(List<BlockEntry> blocks) {
        this.blocks = List.copyOf(blocks);
        TreeMap<String, List<String>> members = new TreeMap<>();
        for (BlockEntry block : blocks) {
            for (String tag : block.tags()) {
                members.computeIfAbsent(tag, ignored -> new ArrayList<>()).add(block.id());
            }
        }
        members.replaceAll((tag, values) -> values.stream().distinct().sorted().toList());
        this.tagMembers = Collections.unmodifiableMap(members);
    }

    static FakeOreImportRegistry of(BlockEntry... blocks) {
        return new FakeOreImportRegistry(Arrays.asList(blocks));
    }

    static BlockEntry block(String id, String... tags) {
        return new BlockEntry(id, Set.of(tags));
    }

    @Override
    public List<BlockEntry> blocks() {
        return blocks;
    }

    @Override
    public List<String> tagMembers(String tagId) {
        String normalized = tagId != null && tagId.startsWith("#") ? tagId.substring(1) : tagId;
        return tagMembers.getOrDefault(normalized, List.of());
    }
}
