package com.nightsta69.delvefold.world;

import com.nightsta69.delvefold.Delvefold;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.TerrainVariant;
import com.nightsta69.delvefold.world.feature.GenerationSaltedLandmarkPlacement;
import com.nightsta69.delvefold.world.feature.GeologyThemeFeature;
import com.nightsta69.delvefold.world.feature.MiningLandmarkFeature;
import com.nightsta69.delvefold.world.feature.MiningOreFeature;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Static registrations and resource keys for Delvefold world generation.
 *
 * <p>The three terrain variants deliberately use separate level keys. A server may change which one its portal targets
 * without ever changing the generator attached to an existing level and producing seams between old and new chunks.
 */
public final class DelvefoldWorldgen {
    private static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, Delvefold.MOD_ID);
    private static final DeferredRegister<PlacementModifierType<?>> PLACEMENT_MODIFIERS =
            DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, Delvefold.MOD_ID);

    public static final DeferredHolder<Feature<?>, MiningOreFeature> MINING_ORE_FEATURE =
            FEATURES.register("mining_ores", MiningOreFeature::new);
    public static final DeferredHolder<Feature<?>, GeologyThemeFeature> GEOLOGY_THEME_FEATURE =
            FEATURES.register("geology_theme", GeologyThemeFeature::new);
    public static final DeferredHolder<Feature<?>, MiningLandmarkFeature> MINING_LANDMARK_FEATURE = FEATURES.register(
            "mining_landmark",
            () -> new MiningLandmarkFeature(
                    net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<
                    PlacementModifierType<?>, PlacementModifierType<GenerationSaltedLandmarkPlacement>>
            GENERATION_SALTED_LANDMARK_PLACEMENT = PLACEMENT_MODIFIERS.register(
                    "generation_salted_landmark", () -> () -> GenerationSaltedLandmarkPlacement.CODEC);

    public static final ResourceKey<Level> FLAT_LEVEL = levelKey("delve_flat");
    public static final ResourceKey<Level> CAVERN_LEVEL = levelKey("delve_cavern");
    public static final ResourceKey<Level> WILD_LEVEL = levelKey("delve_wild");
    public static final ResourceKey<Level> FLAT_EXPANSIVE_LEVEL = levelKey("delve_flat_expansive");
    public static final ResourceKey<Level> CAVERN_EXPANSIVE_LEVEL = levelKey("delve_cavern_expansive");
    public static final ResourceKey<Level> WILD_EXPANSIVE_LEVEL = levelKey("delve_wild_expansive");

    public static final ResourceKey<LevelStem> FLAT_LEVEL_STEM = levelStemKey("delve_flat");
    public static final ResourceKey<LevelStem> CAVERN_LEVEL_STEM = levelStemKey("delve_cavern");
    public static final ResourceKey<LevelStem> WILD_LEVEL_STEM = levelStemKey("delve_wild");
    public static final ResourceKey<LevelStem> FLAT_EXPANSIVE_LEVEL_STEM = levelStemKey("delve_flat_expansive");
    public static final ResourceKey<LevelStem> CAVERN_EXPANSIVE_LEVEL_STEM = levelStemKey("delve_cavern_expansive");
    public static final ResourceKey<LevelStem> WILD_EXPANSIVE_LEVEL_STEM = levelStemKey("delve_wild_expansive");

    public static final ResourceKey<Biome> MINING_FLAT_BIOME = biomeKey("mining_flat");
    public static final ResourceKey<Biome> MINING_CAVERN_BIOME = biomeKey("mining_cavern");
    public static final ResourceKey<Biome> MINING_WILD_BIOME = biomeKey("mining_wild");

    public static final ResourceKey<ConfiguredFeature<?, ?>> MINING_ORES_CONFIGURED =
            ResourceKey.create(Registries.CONFIGURED_FEATURE, id("mining_ores"));
    public static final ResourceKey<PlacedFeature> MINING_ORES_PLACED =
            ResourceKey.create(Registries.PLACED_FEATURE, id("mining_ores"));
    public static final ResourceKey<ConfiguredFeature<?, ?>> GEOLOGY_STRATA_CONFIGURED =
            ResourceKey.create(Registries.CONFIGURED_FEATURE, id("geology_strata"));
    public static final ResourceKey<PlacedFeature> GEOLOGY_STRATA_PLACED =
            ResourceKey.create(Registries.PLACED_FEATURE, id("geology_strata"));
    public static final ResourceKey<ConfiguredFeature<?, ?>> GEOLOGY_DECORATIONS_CONFIGURED =
            ResourceKey.create(Registries.CONFIGURED_FEATURE, id("geology_decorations"));
    public static final ResourceKey<PlacedFeature> GEOLOGY_DECORATIONS_PLACED =
            ResourceKey.create(Registries.PLACED_FEATURE, id("geology_decorations"));
    public static final ResourceKey<ConfiguredFeature<?, ?>> MINING_LANDMARK_CONFIGURED =
            ResourceKey.create(Registries.CONFIGURED_FEATURE, id("mining_landmark"));
    public static final ResourceKey<PlacedFeature> MINING_LANDMARK_PLACED =
            ResourceKey.create(Registries.PLACED_FEATURE, id("mining_landmark"));

    private DelvefoldWorldgen() {}

    public static void register(IEventBus modEventBus) {
        FEATURES.register(modEventBus);
        PLACEMENT_MODIFIERS.register(modEventBus);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Delvefold.MOD_ID, path);
    }

    public static ResourceKey<Level> levelFor(TerrainMode mode, TerrainVariant variant) {
        boolean expansive = variant == TerrainVariant.EXPANSIVE;
        return switch (mode) {
            case FLAT -> expansive ? FLAT_EXPANSIVE_LEVEL : FLAT_LEVEL;
            case CAVERN -> expansive ? CAVERN_EXPANSIVE_LEVEL : CAVERN_LEVEL;
            case WILD -> expansive ? WILD_EXPANSIVE_LEVEL : WILD_LEVEL;
        };
    }

    public static boolean isMiningLevel(ResourceKey<Level> key) {
        return key.equals(FLAT_LEVEL)
                || key.equals(CAVERN_LEVEL)
                || key.equals(WILD_LEVEL)
                || key.equals(FLAT_EXPANSIVE_LEVEL)
                || key.equals(CAVERN_EXPANSIVE_LEVEL)
                || key.equals(WILD_EXPANSIVE_LEVEL);
    }

    public static boolean isCavernLevel(ResourceKey<Level> key) {
        return key.equals(CAVERN_LEVEL) || key.equals(CAVERN_EXPANSIVE_LEVEL);
    }

    public static TerrainMode terrainFor(ResourceKey<Level> key) {
        if (key.equals(FLAT_LEVEL) || key.equals(FLAT_EXPANSIVE_LEVEL)) {
            return TerrainMode.FLAT;
        }
        if (key.equals(CAVERN_LEVEL) || key.equals(CAVERN_EXPANSIVE_LEVEL)) {
            return TerrainMode.CAVERN;
        }
        if (key.equals(WILD_LEVEL) || key.equals(WILD_EXPANSIVE_LEVEL)) {
            return TerrainMode.WILD;
        }
        return null;
    }

    public static TerrainMode terrainFor(Holder<Biome> biome) {
        if (biome.is(MINING_FLAT_BIOME)) {
            return TerrainMode.FLAT;
        }
        if (biome.is(MINING_CAVERN_BIOME)) {
            return TerrainMode.CAVERN;
        }
        if (biome.is(MINING_WILD_BIOME)) {
            return TerrainMode.WILD;
        }
        return null;
    }

    public static ResourceKey<Biome> biomeFor(TerrainMode mode) {
        return switch (mode) {
            case FLAT -> MINING_FLAT_BIOME;
            case CAVERN -> MINING_CAVERN_BIOME;
            case WILD -> MINING_WILD_BIOME;
        };
    }

    private static ResourceKey<Level> levelKey(String path) {
        return ResourceKey.create(Registries.DIMENSION, id(path));
    }

    private static ResourceKey<LevelStem> levelStemKey(String path) {
        return ResourceKey.create(Registries.LEVEL_STEM, id(path));
    }

    private static ResourceKey<Biome> biomeKey(String path) {
        return ResourceKey.create(Registries.BIOME, id(path));
    }
}
