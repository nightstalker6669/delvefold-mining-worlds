package com.nightsta69.delvefold.command;

import com.nightsta69.delvefold.config.model.BiomeFilter;
import com.nightsta69.delvefold.config.model.OreRule;
import com.nightsta69.delvefold.config.model.OreTarget;
import com.nightsta69.delvefold.config.model.SpawnBand;
import com.nightsta69.delvefold.config.model.TerrainMode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

public final class OreRuleFactory {
    private static final String STONE_HOST = "minecraft:stone_ore_replaceables";
    private static final String DEEPSLATE_HOST = "minecraft:deepslate_ore_replaceables";

    private OreRuleFactory() {
    }

    public static OreRule create(ResourceLocation selected, boolean detectVariants, Rarity rarity) {
        if (!BuiltInRegistries.BLOCK.containsKey(selected)) {
            throw new IllegalArgumentException("Block is not registered: " + selected);
        }
        List<OreTarget> targets = detectVariants ? detectTargets(selected) : List.of(exactTarget(selected));
        String id = uniqueSafeId(selected);
        SpawnBand band = switch (rarity) {
            case COMMON -> SpawnBand.triangle("main", 10, 16.0D, -64, 160, 32, 0.0D);
            case UNCOMMON -> SpawnBand.triangle("main", 8, 8.0D, -64, 128, 16, 0.0D);
            case RARE -> SpawnBand.triangle("main", 6, 4.0D, -64, 96, 0, 0.25D);
            case VERY_RARE -> SpawnBand.triangle("main", 4, 1.0D, -64, 64, -32, 0.5D);
        };
        return new OreRule(
                id,
                true,
                false,
                EnumSet.allOf(TerrainMode.class),
                targets,
                BiomeFilter.ALL_MINING_BIOMES,
                List.of(band)
        );
    }

    private static List<OreTarget> detectTargets(ResourceLocation selected) {
        String namespace = selected.getNamespace();
        String path = selected.getPath();
        String base = path.startsWith("deepslate_") ? path.substring("deepslate_".length()) : path;
        ResourceLocation stone = ResourceLocation.fromNamespaceAndPath(namespace, base);
        ResourceLocation deepslate = ResourceLocation.fromNamespaceAndPath(namespace, "deepslate_" + base);
        List<OreTarget> targets = new ArrayList<>(2);
        if (BuiltInRegistries.BLOCK.containsKey(stone)) {
            targets.add(OreTarget.of(stone.toString(), STONE_HOST));
        }
        if (BuiltInRegistries.BLOCK.containsKey(deepslate)) {
            targets.add(OreTarget.of(deepslate.toString(), DEEPSLATE_HOST));
        }
        if (targets.isEmpty()) {
            targets.add(exactTarget(selected));
        }
        return List.copyOf(targets);
    }

    private static OreTarget exactTarget(ResourceLocation selected) {
        String host = selected.getPath().startsWith("deepslate_") ? DEEPSLATE_HOST : STONE_HOST;
        return OreTarget.of(selected.toString(), host);
    }

    private static String uniqueSafeId(ResourceLocation selected) {
        return (selected.getNamespace() + '_' + selected.getPath())
                .toLowerCase(Locale.ROOT)
                .replace('/', '_');
    }

    public enum Rarity {
        COMMON,
        UNCOMMON,
        RARE,
        VERY_RARE;

        public static Rarity parse(String value) {
            return valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_'));
        }
    }
}
