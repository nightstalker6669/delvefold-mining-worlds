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

/** Builds validated command-line ore rules from registered block selections and rarity templates. */
public final class OreRuleFactory {
    private static final String STONE_HOST = "minecraft:stone_ore_replaceables";
    private static final String DEEPSLATE_HOST = "minecraft:deepslate_ore_replaceables";

    private OreRuleFactory() {}

    /**
     * Creates an optional all-terrain rule without mutating or activating any profile.
     *
     * @param selected registered output block identifier
     * @param detectVariants whether conventional stone/deepslate siblings should become host-specific targets
     * @param rarity template controlling attempts, height distribution, and vein size
     * @return an immutable rule whose identifier is derived deterministically from the block identifier
     * @throws IllegalArgumentException if {@code selected} is not registered
     */
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

    /** Command-facing rarity templates for newly added ore rules. */
    public enum Rarity {
        /** Frequent placement using the built-in common band. */
        COMMON,

        /** Moderate placement using the built-in uncommon band. */
        UNCOMMON,

        /** Sparse placement using the built-in rare band. */
        RARE,

        /** Most restrictive placement using the built-in very-rare band. */
        VERY_RARE;

        /**
         * Parses a case-insensitive command token, accepting hyphens in place of underscores.
         *
         * @param value rarity token
         * @return matching rarity
         * @throws IllegalArgumentException if the token does not name a supported rarity
         */
        public static Rarity parse(String value) {
            return valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_'));
        }
    }
}
