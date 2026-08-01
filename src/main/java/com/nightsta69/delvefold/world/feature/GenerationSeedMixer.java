package com.nightsta69.delvefold.world.feature;

/** Pure deterministic seed domains shared by Delvefold's runtime world-generation features. */
final class GenerationSeedMixer {
    private static final long CHUNK_SALT = 0x9E3779B97F4A7C15L;
    private static final long ORE_ID_SALT = 0xD1B54A32D192ED03L;
    private static final long ORE_GENERATION_DOMAIN = 0x6A09E667F3BCC909L;
    private static final long LANDMARK_GENERATION_DOMAIN = 0xBB67AE8584CAA73BL;
    private static final long LANDMARK_PLACEMENT_DOMAIN = 0x3C6EF372FE94F82BL;
    private static final long LANDMARK_CONTENT_DOMAIN = 0xA54FF53A5F1D36F1L;

    private GenerationSeedMixer() {
    }

    static long oreSeed(long worldSeed, long chunkPosition, String id, long generationSalt) {
        long effectiveWorldSeed = generationSalt == 0L
                ? worldSeed
                : worldSeed ^ mix64(generationSalt ^ ORE_GENERATION_DOMAIN);
        long seed = effectiveWorldSeed ^ chunkPosition * CHUNK_SALT;
        seed ^= stableHash64(id) * ORE_ID_SALT;
        return mix64(seed);
    }

    static long landmarkPlacementSeed(long worldSeed, long chunkPosition, long generationSalt) {
        long seed = worldSeed ^ chunkPosition * CHUNK_SALT;
        seed ^= mix64(generationSalt ^ LANDMARK_GENERATION_DOMAIN);
        return mix64(seed ^ LANDMARK_PLACEMENT_DOMAIN);
    }

    static long landmarkContentSeed(long worldSeed, long chunkPosition, long generationSalt) {
        long seed = worldSeed ^ chunkPosition * CHUNK_SALT;
        seed ^= mix64(generationSalt ^ LANDMARK_GENERATION_DOMAIN);
        return mix64(seed ^ LANDMARK_CONTENT_DOMAIN);
    }

    static long mix64(long value) {
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
