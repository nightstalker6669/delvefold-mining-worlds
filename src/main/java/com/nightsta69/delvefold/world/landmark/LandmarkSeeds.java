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

    private LandmarkSeeds() {}

    /**
     * Derives the seed supplied to Minecraft's structure-placement grid.
     *
     * <p>A generation salt of zero returns the world seed unchanged for compatibility. Nonzero salts are mixed after
     * fixed generation and placement domain constants, so recreation rotates placement without changing dimensions.
     *
     * @param worldSeed server world's 64-bit generation seed
     * @param generationSalt persisted salt for the created mining-world generation
     * @return effective seed for structure spacing
     */
    public static long placementWorldSeed(long worldSeed, long generationSalt) {
        return generationSalt == 0L
                ? worldSeed
                : worldSeed ^ mix64(generationSalt ^ GENERATION_DOMAIN ^ PLACEMENT_DOMAIN);
    }

    /**
     * Derives the independent landmark-density acceptance stream for a chunk.
     *
     * @param worldSeed server world's 64-bit generation seed
     * @param chunk candidate chunk coordinates
     * @param generationSalt persisted mining-world generation salt
     * @return deterministic acceptance seed
     */
    public static long acceptanceSeed(long worldSeed, ChunkPos chunk, long generationSalt) {
        return domainSeed(worldSeed, chunk, generationSalt, ACCEPTANCE_DOMAIN);
    }

    /**
     * Derives the weighted-definition selection stream for a chunk.
     *
     * @param worldSeed server world's 64-bit generation seed
     * @param chunk candidate chunk coordinates
     * @param generationSalt persisted mining-world generation salt
     * @return deterministic selection seed independent of acceptance
     */
    public static long selectionSeed(long worldSeed, ChunkPos chunk, long generationSalt) {
        return domainSeed(worldSeed, chunk, generationSalt, SELECTION_DOMAIN);
    }

    /**
     * Derives rotation, placement-height, and template-content randomness for one definition.
     *
     * <p>Inputs are mixed in the stable order world/generation, chunk, content domain, then landmark registry ID.
     * Adding or reordering other catalog definitions therefore does not perturb this definition's content stream.
     *
     * @param worldSeed server world's 64-bit generation seed
     * @param chunk candidate chunk coordinates
     * @param generationSalt persisted mining-world generation salt
     * @param definitionId complete landmark definition registry ID
     * @return deterministic content seed for that definition in that chunk
     */
    public static long definitionSeed(
            long worldSeed, ChunkPos chunk, long generationSalt, ResourceLocation definitionId) {
        return mix64(
                domainSeed(worldSeed, chunk, generationSalt, CONTENT_DOMAIN) ^ stableHash64(definitionId.toString()));
    }

    /**
     * Derives a one-time loot seed for a template data marker.
     *
     * @param contentSeed definition content seed from {@link #definitionSeed(long, ChunkPos, long, ResourceLocation)}
     * @param markerPosition packed block position of the structure data marker
     * @return deterministic loot-table seed independent for that marker position
     */
    public static long lootSeed(long contentSeed, long markerPosition) {
        return mix64(contentSeed ^ markerPosition ^ LOOT_DOMAIN);
    }

    private static long domainSeed(long worldSeed, ChunkPos chunk, long generationSalt, long domain) {
        long seed = placementWorldSeed(worldSeed, generationSalt);
        seed ^= chunk.toLong() * 0x9E3779B97F4A7C15L;
        return mix64(seed ^ domain);
    }

    /**
     * Applies the fixed 64-bit avalanche mixer used by all landmark seed domains.
     *
     * @param value unmixed 64-bit input
     * @return deterministically avalanched value
     */
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
