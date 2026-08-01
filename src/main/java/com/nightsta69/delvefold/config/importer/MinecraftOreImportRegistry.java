package com.nightsta69.delvefold.config.importer;

import com.mojang.datafixers.util.Pair;
import com.nightsta69.delvefold.config.validation.RegistryLookup;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/** Immutable snapshot of the currently bound Minecraft block registry and tags. */
public final class MinecraftOreImportRegistry implements OreImportRegistry, RegistryLookup {
    private static final Object CACHE_LOCK = new Object();
    private static volatile @Nullable CachedSnapshot cachedSnapshot;

    private final List<BlockEntry> blocks;
    private final Map<String, List<String>> tagMembers;
    private final Set<String> blockIds;

    /**
     * Captures the currently bound Minecraft block registry and tag membership into immutable collections. Block
     * entries and each tag's member list are ordered lexically by registry ID.
     */
    public MinecraftOreImportRegistry() {
        TreeMap<String, Set<String>> tagsByBlock = new TreeMap<>();
        for (ResourceLocation id : BuiltInRegistries.BLOCK.keySet()) {
            tagsByBlock.put(id.toString(), new TreeSet<>());
        }

        TreeMap<String, List<String>> membersByTag = new TreeMap<>();
        BuiltInRegistries.BLOCK
                .getTags()
                .sorted(java.util.Comparator.comparing(pair -> pair.getFirst().location()))
                .forEach(pair -> captureTag(pair, tagsByBlock, membersByTag));

        this.blocks = tagsByBlock.entrySet().stream()
                .map(entry -> new BlockEntry(entry.getKey(), entry.getValue()))
                .toList();
        this.blockIds = Set.copyOf(tagsByBlock.keySet());
        this.tagMembers = Collections.unmodifiableMap(new TreeMap<>(membersByTag));
    }

    /**
     * Returns the immutable registry/tag snapshot and its deterministic fingerprint for the current server resource
     * state. The expensive registry walk and SHA-256 calculation happen at most once between lifecycle or
     * datapack-reload invalidations.
     *
     * @return shared immutable registry snapshot and matching deterministic fingerprint
     */
    public static CachedSnapshot cachedSnapshot() {
        CachedSnapshot current = cachedSnapshot;
        if (current != null) {
            return current;
        }
        synchronized (CACHE_LOCK) {
            current = cachedSnapshot;
            if (current == null) {
                MinecraftOreImportRegistry registry = new MinecraftOreImportRegistry();
                current = new CachedSnapshot(registry, OreImportFingerprints.registry(registry));
                cachedSnapshot = current;
            }
            return current;
        }
    }

    /** Discards the cached resource-state snapshot; the next authorized request rebuilds it. */
    public static void invalidateCache() {
        synchronized (CACHE_LOCK) {
            cachedSnapshot = null;
        }
    }

    /**
     * Returns all captured blocks in lexical registry-ID order.
     *
     * @return immutable captured block entries
     */
    @Override
    public List<BlockEntry> blocks() {
        return blocks;
    }

    /**
     * Looks up captured members of a block tag.
     *
     * @param tagId block-tag ID, with or without a leading {@code #}
     * @return immutable members in lexical registry-ID order, or an empty list for a missing tag
     */
    @Override
    public List<String> tagMembers(String tagId) {
        String normalized = stripHash(tagId);
        return tagMembers.getOrDefault(normalized, List.of());
    }

    /**
     * Tests exact block-ID presence in the captured snapshot.
     *
     * @param id block registry ID to test
     * @return {@code true} when the trimmed ID was captured
     */
    @Override
    public boolean blockExists(String id) {
        return id != null && blockIds.contains(id.trim());
    }

    /**
     * Tests whether a block tag exists with at least one captured member.
     *
     * @param id block-tag ID, with or without a leading {@code #}
     * @return {@code true} when the tag has one or more captured members
     */
    @Override
    public boolean blockTagExists(String id) {
        return !tagMembers(id).isEmpty();
    }

    private static void captureTag(
            Pair<TagKey<Block>, HolderSet.Named<Block>> pair,
            Map<String, Set<String>> tagsByBlock,
            Map<String, List<String>> membersByTag) {
        String tagId = pair.getFirst().location().toString();
        List<String> members = new ArrayList<>();
        for (Holder<Block> holder : pair.getSecond()) {
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(holder.value());
            if (blockId == null) {
                continue;
            }
            String serialized = blockId.toString();
            members.add(serialized);
            tagsByBlock.computeIfAbsent(serialized, ignored -> new TreeSet<>()).add(tagId);
        }
        members.sort(String::compareTo);
        membersByTag.put(tagId, List.copyOf(members));
    }

    private static String stripHash(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.startsWith("#") ? normalized.substring(1) : normalized;
    }

    /**
     * Atomically published registry snapshot and the fingerprint computed from that exact snapshot.
     *
     * @param registry immutable captured Minecraft registry
     * @param fingerprint deterministic lowercase hexadecimal SHA-256 binding
     */
    public record CachedSnapshot(MinecraftOreImportRegistry registry, String fingerprint) {
        /**
         * Requires both parts of the atomically cached value.
         *
         * @param registry immutable captured Minecraft registry
         * @param fingerprint fingerprint computed from {@code registry}
         */
        public CachedSnapshot {
            java.util.Objects.requireNonNull(registry, "registry");
            java.util.Objects.requireNonNull(fingerprint, "fingerprint");
        }
    }
}
