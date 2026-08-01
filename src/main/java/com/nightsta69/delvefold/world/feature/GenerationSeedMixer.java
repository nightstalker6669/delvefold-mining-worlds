package com.nightsta69.delvefold.world.feature;

/** Pure deterministic seed domains shared by Delvefold's runtime world-generation features. */
final class GenerationSeedMixer {
    private static final long CHUNK_SALT = 0x9E3779B97F4A7C15L;
    private static final long ORE_ID_SALT = 0xD1B54A32D192ED03L;
    private static final long ORE_GENERATION_DOMAIN = 0x6A09E667F3BCC909L;
    private static final long LANDMARK_GENERATION_DOMAIN = 0xBB67AE8584CAA73BL;
    private static final long LANDMARK_PLACEMENT_DOMAIN = 0x3C6EF372FE94F82BL;
    private static final long LANDMARK_CONTENT_DOMAIN = 0xA54FF53A5F1D36F1L;
    private static final long PROVINCE_CENTER_DOMAIN = 0x510E527FADE682D1L;
    private static final long PROVINCE_OUTPUT_DOMAIN = 0x9B05688C2B3E6C1FL;
    private static final long PROVINCE_CHUNK_DOMAIN = 0x1F83D9ABFB41BD6BL;
    private static final long PROVINCE_POSITION_DOMAIN = 0x5BE0CD19137E2179L;
    private static final long REGION_X_SALT = 0xD6E8FEB86659FD93L;
    private static final long REGION_Z_SALT = 0xA5A3564E27F8862BL;
    private static final long GEOLOGY_GENERATION_DOMAIN = 0xCBBB9D5DC1059ED8L;
    private static final long GEOLOGY_STRATA_DOMAIN = 0x629A292A367CD507L;
    private static final long GEOLOGY_DECORATION_DOMAIN = 0x9159015A3070DD17L;

    private GenerationSeedMixer() {}

    static long oreSeed(long worldSeed, long chunkPosition, String id, long generationSalt) {
        long effectiveWorldSeed =
                generationSalt == 0L ? worldSeed : worldSeed ^ mix64(generationSalt ^ ORE_GENERATION_DOMAIN);
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

    static long oreProvinceCenterSeed(long worldSeed, long regionX, long regionZ, String bandId, long generationSalt) {
        return provinceSeed(worldSeed, regionX, regionZ, bandId, generationSalt, PROVINCE_CENTER_DOMAIN);
    }

    static long oreProvinceOutputSeed(long worldSeed, long regionX, long regionZ, String bandId, long generationSalt) {
        return provinceSeed(worldSeed, regionX, regionZ, bandId, generationSalt, PROVINCE_OUTPUT_DOMAIN);
    }

    static long oreProvinceChunkSeed(
            long worldSeed, long regionX, long regionZ, long chunkPosition, String bandId, long generationSalt) {
        long seed = provinceSeed(worldSeed, regionX, regionZ, bandId, generationSalt, PROVINCE_CHUNK_DOMAIN);
        return mix64(seed ^ mix64(chunkPosition * CHUNK_SALT));
    }

    static long oreProvincePositionSeed(long chunkSeed, int linearIndex) {
        return mix64(chunkSeed ^ PROVINCE_POSITION_DOMAIN ^ (linearIndex + 1L) * ORE_ID_SALT);
    }

    private static long provinceSeed(
            long worldSeed, long regionX, long regionZ, String bandId, long generationSalt, long domain) {
        long seed = worldSeed ^ domain;
        seed ^= mix64(regionX * REGION_X_SALT);
        seed ^= mix64(regionZ * REGION_Z_SALT);
        seed ^= mix64(stableHash64(bandId) * ORE_ID_SALT);
        if (generationSalt != 0L) {
            seed ^= mix64(generationSalt ^ ORE_GENERATION_DOMAIN);
        }
        return mix64(seed);
    }

    static long geologySeed(long worldSeed, long chunkPosition, long generationSalt, String themeId, boolean strata) {
        long seed = worldSeed ^ chunkPosition * CHUNK_SALT;
        seed ^= mix64(generationSalt ^ GEOLOGY_GENERATION_DOMAIN);
        seed ^= mix64(stableHash64(themeId) * ORE_ID_SALT);
        return mix64(seed ^ (strata ? GEOLOGY_STRATA_DOMAIN : GEOLOGY_DECORATION_DOMAIN));
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
