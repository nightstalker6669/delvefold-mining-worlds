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

/** Immutable snapshot of the currently bound Minecraft block registry and tags. */
public final class MinecraftOreImportRegistry implements OreImportRegistry, RegistryLookup {
    private static final Object CACHE_LOCK = new Object();
    private static volatile CachedSnapshot cachedSnapshot;

    private final List<BlockEntry> blocks;
    private final Map<String, List<String>> tagMembers;
    private final Set<String> blockIds;

    public MinecraftOreImportRegistry() {
        TreeMap<String, TreeSet<String>> tagsByBlock = new TreeMap<>();
        for (ResourceLocation id : BuiltInRegistries.BLOCK.keySet()) {
            tagsByBlock.put(id.toString(), new TreeSet<>());
        }

        TreeMap<String, List<String>> membersByTag = new TreeMap<>();
        BuiltInRegistries.BLOCK.getTags()
                .sorted(java.util.Comparator.comparing(pair -> pair.getFirst().location()))
                .forEach(pair -> captureTag(pair, tagsByBlock, membersByTag));

        this.blocks = tagsByBlock.entrySet().stream()
                .map(entry -> new BlockEntry(entry.getKey(), entry.getValue()))
                .toList();
        this.blockIds = Set.copyOf(tagsByBlock.keySet());
        this.tagMembers = Collections.unmodifiableMap(new TreeMap<>(membersByTag));
    }

    /**
     * Returns the immutable registry/tag snapshot and its deterministic fingerprint for the
     * current server resource state. The expensive registry walk and SHA-256 calculation happen
     * at most once between lifecycle or datapack-reload invalidations.
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

    @Override
    public List<BlockEntry> blocks() {
        return blocks;
    }

    @Override
    public List<String> tagMembers(String tagId) {
        String normalized = stripHash(tagId);
        return tagMembers.getOrDefault(normalized, List.of());
    }

    @Override
    public boolean blockExists(String id) {
        return id != null && blockIds.contains(id.trim());
    }

    @Override
    public boolean blockTagExists(String id) {
        return !tagMembers(id).isEmpty();
    }

    private static void captureTag(
            Pair<TagKey<Block>, HolderSet.Named<Block>> pair,
            Map<String, TreeSet<String>> tagsByBlock,
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

    public record CachedSnapshot(MinecraftOreImportRegistry registry, String fingerprint) {
        public CachedSnapshot {
            registry = java.util.Objects.requireNonNull(registry, "registry");
            fingerprint = java.util.Objects.requireNonNull(fingerprint, "fingerprint");
        }
    }
}
