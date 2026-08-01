package com.nightsta69.delvefold.qualityruntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class GenerationSeedCompatibilityCharacterizationTest {
    private static final String MIXER = "com.nightsta69.delvefold.world.feature.GenerationSeedMixer";

    @Test
    void oreAndLandmarkSeedDomainsMatchTheReleasedGoldenVectors() throws ReflectiveOperationException {
        long legacyOre = invoke(
                "oreSeed",
                new Class<?>[] {long.class, long.class, String.class, long.class},
                0x1234_5678_9ABCDEFL,
                chunkPosition(17, -29),
                "example:tin/rich",
                0L);
        long rotatedOre = invoke(
                "oreSeed",
                new Class<?>[] {long.class, long.class, String.class, long.class},
                8_675_309L,
                chunkPosition(-37, 91),
                "diamond/main",
                41L);
        long placement = invoke(
                "landmarkPlacementSeed",
                new Class<?>[] {long.class, long.class, long.class},
                0x7A11_CAFE_BABEL,
                chunkPosition(23, -51),
                101L);
        long content = invoke(
                "landmarkContentSeed",
                new Class<?>[] {long.class, long.class, long.class},
                0x7A11_CAFE_BABEL,
                chunkPosition(23, -51),
                101L);

        assertEquals(8_501_807_808_818_706_786L, legacyOre);
        assertEquals(5_441_939_300_492_376_483L, rotatedOre);
        assertEquals(2_802_519_697_248_467_934L, placement);
        assertEquals(7_547_251_648_831_141_664L, content);
        assertNotEquals(placement, content, "landmark placement and content must stay domain-separated");
    }

    @Test
    void provinceAndGeologySeedDomainsMatchTheReleasedGoldenVectors() throws ReflectiveOperationException {
        Class<?>[] provinceTypes = {long.class, long.class, long.class, String.class, long.class};
        long center = invoke("oreProvinceCenterSeed", provinceTypes, 1_234L, -7L, 11L, "diamond/province", 41L);
        long output = invoke("oreProvinceOutputSeed", provinceTypes, 1_234L, -7L, 11L, "diamond/province", 41L);
        long chunk = invoke(
                "oreProvinceChunkSeed",
                new Class<?>[] {long.class, long.class, long.class, long.class, String.class, long.class},
                1_234L,
                -7L,
                11L,
                chunkPosition(4, -2),
                "diamond/province",
                41L);
        long strata = invoke(
                "geologySeed",
                new Class<?>[] {long.class, long.class, long.class, String.class, boolean.class},
                23_456_789L,
                chunkPosition(-8, 44),
                73L,
                "volcanic",
                true);
        long decoration = invoke(
                "geologySeed",
                new Class<?>[] {long.class, long.class, long.class, String.class, boolean.class},
                23_456_789L,
                chunkPosition(-8, 44),
                73L,
                "volcanic",
                false);

        assertEquals(-4_821_356_874_300_743_481L, center);
        assertEquals(-6_157_807_262_665_512_548L, output);
        assertEquals(-5_881_509_919_245_958_414L, chunk);
        assertEquals(-3_263_343_590_043_391_405L, strata);
        assertEquals(-3_659_284_070_069_997_977L, decoration);
        assertNotEquals(strata, decoration, "strata and decoration must stay domain-separated");
    }

    private static long invoke(String name, Class<?>[] parameterTypes, Object... arguments)
            throws ReflectiveOperationException {
        Method method = Class.forName(MIXER).getDeclaredMethod(name, parameterTypes);
        assertTrue(method.trySetAccessible(), () -> "Cannot access deterministic seed seam " + name);
        return assertInstanceOf(Long.class, method.invoke(null, arguments));
    }

    private static long chunkPosition(int x, int z) {
        return ((long) x & 0xFFFF_FFFFL) | (((long) z & 0xFFFF_FFFFL) << 32);
    }
}
