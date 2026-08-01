package com.nightsta69.delvefold.world.landmark;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

/** Independent deterministic seed domains for structure placement, acceptance, selection, and loot. */
public final class LandmarkSeeds {
    private static final long GENERATION_DOMAIN = 0xBB67AE8584CAA73BL;
    private static final long PLACEMENT_DOMAIN = 0x3C6EF372FE94F82BL;
    private static final long ACCEPTANCE_DOMAIN = 0x510E527FADE682D1L;
    private static final long SELECTION_DOMAIN = 0x9B05688C2B3E6C1FL;
    private static final long CONTENT_DOMAIN = 0xA54FF53A5F1D36F1L;
    private static final long LOOT_DOMAIN = 0x1F83D9ABFB41BD6BL;

    private LandmarkSeeds() {
    }

    public static long placementWorldSeed(long worldSeed, long generationSalt) {
        return generationSalt == 0L ? worldSeed
                : worldSeed ^ mix64(generationSalt ^ GENERATION_DOMAIN ^ PLACEMENT_DOMAIN);
    }

    public static long acceptanceSeed(long worldSeed, ChunkPos chunk, long generationSalt) {
        return domainSeed(worldSeed, chunk, generationSalt, ACCEPTANCE_DOMAIN);
    }

    public static long selectionSeed(long worldSeed, ChunkPos chunk, long generationSalt) {
        return domainSeed(worldSeed, chunk, generationSalt, SELECTION_DOMAIN);
    }

    public static long definitionSeed(
            long worldSeed, ChunkPos chunk, long generationSalt, ResourceLocation definitionId) {
        return mix64(domainSeed(worldSeed, chunk, generationSalt, CONTENT_DOMAIN)
                ^ stableHash64(definitionId.toString()));
    }

    public static long lootSeed(long contentSeed, long markerPosition) {
        return mix64(contentSeed ^ markerPosition ^ LOOT_DOMAIN);
    }

    private static long domainSeed(long worldSeed, ChunkPos chunk, long generationSalt, long domain) {
        long seed = placementWorldSeed(worldSeed, generationSalt);
        seed ^= chunk.toLong() * 0x9E3779B97F4A7C15L;
        return mix64(seed ^ domain);
    }

    public static long mix64(long value) {
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private static long stableHash64(String value) {
        long hash = 0xCBF29CE484222325L;
        for (int index = 0; index < value.length(); index++) {
            hash ^= value.charAt(index);
            hash *= 0x100000001B3L;
        }
        return hash;
    }
}
