package com.nightsta69.delvefold.config;

import com.nightsta69.delvefold.config.model.BiomeFilter;
import com.nightsta69.delvefold.config.model.OreRule;
import com.nightsta69.delvefold.config.model.OreTarget;
import com.nightsta69.delvefold.config.model.SpawnBand;
import com.nightsta69.delvefold.config.model.TerrainMode;
import java.util.EnumSet;
import java.util.List;

/**
 * Shared immutable rule templates used by commands and server-authored guided-import plans.
 *
 * <p>Factories return unvalidated model values; the complete proposed profile must pass registry and aggregate work
 * validation before persistence or publication.
 */
public final class OreRuleTemplates {
    private OreRuleTemplates() {}

    /**
     * Returns the common triangular classic-vein template.
     *
     * @return size 10, 16 attempts/chunk, Y -64..160, peak Y 32, and zero air-exposure discard
     */
    public static SpawnBand commonBand() {
        return SpawnBand.triangle("main", 10, 16.0D, -64, 160, 32, 0.0D);
    }

    /**
     * Returns the established uncommon triangular classic-vein template shared by every entry point.
     *
     * @return size 8, 8 attempts/chunk, Y -64..128, peak Y 16, and zero air-exposure discard
     */
    public static SpawnBand uncommonBand() {
        return SpawnBand.triangle("main", 8, 8.0D, -64, 128, 16, 0.0D);
    }

    /**
     * Returns the rare triangular classic-vein template.
     *
     * @return size 6, 4 attempts/chunk, Y -64..96, peak Y 0, and 25-percent air-exposure discard
     */
    public static SpawnBand rareBand() {
        return SpawnBand.triangle("main", 6, 4.0D, -64, 96, 0, 0.25D);
    }

    /**
     * Returns the very-rare triangular classic-vein template.
     *
     * @return size 4, 1 attempt/chunk, Y -64..64, peak Y -32, and 50-percent air-exposure discard
     */
    public static SpawnBand veryRareBand() {
        return SpawnBand.triangle("main", 4, 1.0D, -64, 64, -32, 0.5D);
    }

    /**
     * Builds an optional all-terrain ore rule using the established uncommon band.
     *
     * @param ruleId stable lowercase rule ID
     * @param targets ordered exact or tag-driven outputs
     * @return enabled optional rule for all terrain modes and Delvefold mining biomes
     */
    public static OreRule uncommon(String ruleId, List<OreTarget> targets) {
        return optionalAllTerrain(ruleId, targets, uncommonBand());
    }

    /**
     * Builds an enabled optional all-terrain rule containing one supplied band.
     *
     * <p>Missing output mods warn and skip because {@code required} is false. The returned rule defensively copies the
     * target list and uses {@link BiomeFilter#ALL_MINING_BIOMES}.
     *
     * @param ruleId stable lowercase rule ID
     * @param targets ordered exact or tag-driven outputs
     * @param band single independently salted placement band
     * @return unvalidated immutable rule for every terrain mode
     */
    public static OreRule optionalAllTerrain(String ruleId, List<OreTarget> targets, SpawnBand band) {
        return new OreRule(
                ruleId,
                true,
                false,
                EnumSet.allOf(TerrainMode.class),
                targets,
                BiomeFilter.ALL_MINING_BIOMES,
                List.of(band));
    }
}
