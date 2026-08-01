package com.nightsta69.delvefold.config;

import com.nightsta69.delvefold.config.model.BiomeFilter;
import com.nightsta69.delvefold.config.model.OreRule;
import com.nightsta69.delvefold.config.model.OreTarget;
import com.nightsta69.delvefold.config.model.SpawnBand;
import com.nightsta69.delvefold.config.model.TerrainMode;
import java.util.EnumSet;
import java.util.List;

/** Shared rule templates used by commands and server-authored import plans. */
public final class OreRuleTemplates {
    private OreRuleTemplates() {}

    public static SpawnBand commonBand() {
        return SpawnBand.triangle("main", 10, 16.0D, -64, 160, 32, 0.0D);
    }

    /** The established Uncommon preset; keep this identical for every entry point. */
    public static SpawnBand uncommonBand() {
        return SpawnBand.triangle("main", 8, 8.0D, -64, 128, 16, 0.0D);
    }

    public static SpawnBand rareBand() {
        return SpawnBand.triangle("main", 6, 4.0D, -64, 96, 0, 0.25D);
    }

    public static SpawnBand veryRareBand() {
        return SpawnBand.triangle("main", 4, 1.0D, -64, 64, -32, 0.5D);
    }

    public static OreRule uncommon(String ruleId, List<OreTarget> targets) {
        return optionalAllTerrain(ruleId, targets, uncommonBand());
    }

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
