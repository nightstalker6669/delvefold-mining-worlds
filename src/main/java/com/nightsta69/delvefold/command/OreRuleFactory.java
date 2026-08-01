package com.nightsta69.delvefold.command;

import com.nightsta69.delvefold.config.OreRuleTemplates;
import com.nightsta69.delvefold.config.model.OreRule;
import com.nightsta69.delvefold.config.model.OreTarget;
import com.nightsta69.delvefold.config.model.SpawnBand;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

public final class OreRuleFactory {
    private static final String STONE_HOST = "minecraft:stone_ore_replaceables";
    private static final String DEEPSLATE_HOST = "minecraft:deepslate_ore_replaceables";

    private OreRuleFactory() {}

    public static OreRule create(ResourceLocation selected, boolean detectVariants, Rarity rarity) {
        if (!BuiltInRegistries.BLOCK.containsKey(selected)) {
            throw new IllegalArgumentException("Block is not registered: " + selected);
        }
        List<OreTarget> targets = detectVariants ? detectTargets(selected) : List.of(exactTarget(selected));
        String id = uniqueSafeId(selected);
        SpawnBand band =
                switch (rarity) {
                    case COMMON -> OreRuleTemplates.commonBand();
                    case UNCOMMON -> OreRuleTemplates.uncommonBand();
                    case RARE -> OreRuleTemplates.rareBand();
                    case VERY_RARE -> OreRuleTemplates.veryRareBand();
                };
        return OreRuleTemplates.optionalAllTerrain(id, targets, band);
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
