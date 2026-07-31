package com.nightsta69.delvefold.config;

import com.nightsta69.delvefold.config.model.BiomeFilter;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.OreRule;
import com.nightsta69.delvefold.config.model.OreTarget;
import com.nightsta69.delvefold.config.model.SpawnBand;
import com.nightsta69.delvefold.config.model.TerrainMode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** Built-in profiles used for explicit initialization and config recovery. */
public final class OrePresets {
    private static final String STONE_HOST = "minecraft:stone_ore_replaceables";
    private static final String DEEPSLATE_HOST = "minecraft:deepslate_ore_replaceables";

    private OrePresets() {
    }

    public static OreProfileDocument create(OrePreset preset) {
        return switch (preset) {
            case VANILLA_BALANCED -> balanced();
            case RICH -> rich();
            case EMPTY -> empty();
        };
    }

    public static OreProfileDocument balanced() {
        List<OreRule> rules = List.of(
                rule("minecraft_coal", targets("minecraft:coal_ore", "minecraft:deepslate_coal_ore"), List.of(
                        SpawnBand.uniform("upper", 17, 20, 136, 320, 0.0),
                        SpawnBand.triangle("lower", 17, 30, 0, 192, 96, 0.0)
                )),
                rule("minecraft_iron", targets("minecraft:iron_ore", "minecraft:deepslate_iron_ore"), List.of(
                        SpawnBand.triangle("high", 9, 90, 80, 320, 232, 0.0),
                        SpawnBand.triangle("middle", 9, 10, -24, 56, 16, 0.0),
                        SpawnBand.uniform("deep", 9, 10, -64, 72, 0.0)
                )),
                rule("minecraft_copper", targets("minecraft:copper_ore", "minecraft:deepslate_copper_ore"), List.of(
                        SpawnBand.triangle("main", 10, 16, -16, 112, 48, 0.0)
                )),
                rule("minecraft_gold", targets("minecraft:gold_ore", "minecraft:deepslate_gold_ore"), List.of(
                        SpawnBand.triangle("main", 9, 4, -64, 32, -16, 0.0)
                )),
                rule("minecraft_redstone", targets("minecraft:redstone_ore", "minecraft:deepslate_redstone_ore"), List.of(
                        SpawnBand.uniform("main", 8, 8, -64, 15, 0.0),
                        SpawnBand.triangle("deep", 8, 4, -64, -32, -64, 0.0)
                )),
                rule("minecraft_lapis", targets("minecraft:lapis_ore", "minecraft:deepslate_lapis_ore"), List.of(
                        SpawnBand.triangle("exposed", 7, 2, -64, 64, 0, 0.0),
                        SpawnBand.uniform("buried", 7, 4, -64, 64, 1.0)
                )),
                rule("minecraft_diamond", targets("minecraft:diamond_ore", "minecraft:deepslate_diamond_ore"), List.of(
                        SpawnBand.triangle("main", 4, 7, -64, 16, -64, 0.5),
                        SpawnBand.triangle("large_buried", 8, 0.5, -64, 16, -64, 1.0)
                )),
                rule("minecraft_emerald", List.of(OreTarget.of("minecraft:emerald_ore", STONE_HOST)), List.of(
                        SpawnBand.triangle("high", 3, 10, -16, 320, 232, 0.0)
                ))
        );
        return new OreProfileDocument(OreProfileDocument.CURRENT_SCHEMA_VERSION, 0, "vanilla_balanced", rules);
    }

    public static OreProfileDocument rich() {
        OreProfileDocument balanced = balanced();
        List<OreRule> richRules = new ArrayList<>(balanced.rules().size());
        for (OreRule rule : balanced.rules()) {
            richRules.add(rule.withBands(rule.bands().stream()
                    .map(band -> band.withAttempts(band.attemptsPerChunk() * 2.0D))
                    .toList()));
        }
        return new OreProfileDocument(OreProfileDocument.CURRENT_SCHEMA_VERSION, 0, "rich", richRules);
    }

    public static OreProfileDocument empty() {
        return new OreProfileDocument(OreProfileDocument.CURRENT_SCHEMA_VERSION, 0, "empty", List.of());
    }

    private static OreRule rule(String id, List<OreTarget> targets, List<SpawnBand> bands) {
        return new OreRule(
                id,
                true,
                true,
                EnumSet.allOf(TerrainMode.class),
                targets,
                BiomeFilter.ALL_MINING_BIOMES,
                bands
        );
    }

    private static List<OreTarget> targets(String stoneOre, String deepslateOre) {
        return List.of(
                OreTarget.of(stoneOre, STONE_HOST),
                OreTarget.of(deepslateOre, DEEPSLATE_HOST)
        );
    }
}
