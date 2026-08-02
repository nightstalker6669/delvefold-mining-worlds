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
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.Nullable;

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

    /** Runtime ore feature registered under {@code delvefold:mining_ores}. */
    public static final DeferredHolder<Feature<?>, MiningOreFeature> MINING_ORE_FEATURE =
            FEATURES.register("mining_ores", MiningOreFeature::new);
    /** Runtime geology-theme feature registered under {@code delvefold:geology_theme}. */
    public static final DeferredHolder<Feature<?>, GeologyThemeFeature> GEOLOGY_THEME_FEATURE =
            FEATURES.register("geology_theme", GeologyThemeFeature::new);
    /** Legacy bounded landmark feature retained for existing configured-feature data. */
    public static final DeferredHolder<Feature<?>, MiningLandmarkFeature> MINING_LANDMARK_FEATURE = FEATURES.register(
            "mining_landmark",
            () -> new MiningLandmarkFeature(
                    net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.CODEC));
    /** Placement modifier type whose random stream incorporates the persisted generation salt. */
    public static final DeferredHolder<
                    PlacementModifierType<?>, PlacementModifierType<GenerationSaltedLandmarkPlacement>>
            GENERATION_SALTED_LANDMARK_PLACEMENT = PLACEMENT_MODIFIERS.register(
                    "generation_salted_landmark", () -> () -> GenerationSaltedLandmarkPlacement.CODEC);

    /** Classic flat mining-dimension key. */
    public static final ResourceKey<Level> FLAT_LEVEL = levelKey("delve_flat");
    /** Classic cavern mining-dimension key. */
    public static final ResourceKey<Level> CAVERN_LEVEL = levelKey("delve_cavern");
    /** Classic overworld-like mining-dimension key. */
    public static final ResourceKey<Level> WILD_LEVEL = levelKey("delve_wild");
    /** Expansive flat mining-dimension key. */
    public static final ResourceKey<Level> FLAT_EXPANSIVE_LEVEL = levelKey("delve_flat_expansive");
    /** Expansive cavern mining-dimension key. */
    public static final ResourceKey<Level> CAVERN_EXPANSIVE_LEVEL = levelKey("delve_cavern_expansive");
    /** Expansive overworld-like mining-dimension key. */
    public static final ResourceKey<Level> WILD_EXPANSIVE_LEVEL = levelKey("delve_wild_expansive");

    /** Level-stem key backing {@link #FLAT_LEVEL}. */
    public static final ResourceKey<LevelStem> FLAT_LEVEL_STEM = levelStemKey("delve_flat");
    /** Level-stem key backing {@link #CAVERN_LEVEL}. */
    public static final ResourceKey<LevelStem> CAVERN_LEVEL_STEM = levelStemKey("delve_cavern");
    /** Level-stem key backing {@link #WILD_LEVEL}. */
    public static final ResourceKey<LevelStem> WILD_LEVEL_STEM = levelStemKey("delve_wild");
    /** Level-stem key backing {@link #FLAT_EXPANSIVE_LEVEL}. */
    public static final ResourceKey<LevelStem> FLAT_EXPANSIVE_LEVEL_STEM = levelStemKey("delve_flat_expansive");
    /** Level-stem key backing {@link #CAVERN_EXPANSIVE_LEVEL}. */
    public static final ResourceKey<LevelStem> CAVERN_EXPANSIVE_LEVEL_STEM = levelStemKey("delve_cavern_expansive");
    /** Level-stem key backing {@link #WILD_EXPANSIVE_LEVEL}. */
    public static final ResourceKey<LevelStem> WILD_EXPANSIVE_LEVEL_STEM = levelStemKey("delve_wild_expansive");

    /** Biome key identifying flat terrain during generation and filtering. */
    public static final ResourceKey<Biome> MINING_FLAT_BIOME = biomeKey("mining_flat");
    /** Biome key identifying cavern terrain during generation and filtering. */
    public static final ResourceKey<Biome> MINING_CAVERN_BIOME = biomeKey("mining_cavern");
    /** Biome key identifying overworld-like terrain during generation and filtering. */
    public static final ResourceKey<Biome> MINING_WILD_BIOME = biomeKey("mining_wild");
    /** Noise settings for the enclosed, stone-surfaced Classic Cavern generator. */
    public static final ResourceKey<NoiseGeneratorSettings> CAVERN_NOISE_SETTINGS =
            ResourceKey.create(Registries.NOISE_SETTINGS, id("delve_cavern"));
    /** Noise settings for the taller enclosed, stone-surfaced Expansive Cavern generator. */
    public static final ResourceKey<NoiseGeneratorSettings> CAVERN_EXPANSIVE_NOISE_SETTINGS =
            ResourceKey.create(Registries.NOISE_SETTINGS, id("delve_cavern_expansive"));

    /** Compact cave carver shared by Classic and Expansive Cavern generators. */
    public static final ResourceKey<ConfiguredWorldCarver<?>> COMPACT_CAVE_CARVER =
            ResourceKey.create(Registries.CONFIGURED_CARVER, id("compact_cave"));

    /** Configured-feature key for runtime-profile ore placement. */
    public static final ResourceKey<ConfiguredFeature<?, ?>> MINING_ORES_CONFIGURED =
            ResourceKey.create(Registries.CONFIGURED_FEATURE, id("mining_ores"));
    /** Placed-feature key that invokes {@link #MINING_ORES_CONFIGURED}. */
    public static final ResourceKey<PlacedFeature> MINING_ORES_PLACED =
            ResourceKey.create(Registries.PLACED_FEATURE, id("mining_ores"));
    /** Configured-feature key for sparse shallow water pockets on Cavern floors. */
    public static final ResourceKey<ConfiguredFeature<?, ?>> CAVERN_WATER_POCKET_CONFIGURED =
            ResourceKey.create(Registries.CONFIGURED_FEATURE, id("cavern_water_pocket"));
    /** Placed-feature key for sparse shallow water pockets on Cavern floors. */
    public static final ResourceKey<PlacedFeature> CAVERN_WATER_POCKET_PLACED =
            ResourceKey.create(Registries.PLACED_FEATURE, id("cavern_water_pocket"));
    /** Configured-feature key for geology strata. */
    public static final ResourceKey<ConfiguredFeature<?, ?>> GEOLOGY_STRATA_CONFIGURED =
            ResourceKey.create(Registries.CONFIGURED_FEATURE, id("geology_strata"));
    /** Placed-feature key for geology strata. */
    public static final ResourceKey<PlacedFeature> GEOLOGY_STRATA_PLACED =
            ResourceKey.create(Registries.PLACED_FEATURE, id("geology_strata"));
    /** Configured-feature key for bounded geology decorations. */
    public static final ResourceKey<ConfiguredFeature<?, ?>> GEOLOGY_DECORATIONS_CONFIGURED =
            ResourceKey.create(Registries.CONFIGURED_FEATURE, id("geology_decorations"));
    /** Placed-feature key for bounded geology decorations. */
    public static final ResourceKey<PlacedFeature> GEOLOGY_DECORATIONS_PLACED =
            ResourceKey.create(Registries.PLACED_FEATURE, id("geology_decorations"));
    /** Configured-feature key for the legacy landmark feature. */
    public static final ResourceKey<ConfiguredFeature<?, ?>> MINING_LANDMARK_CONFIGURED =
            ResourceKey.create(Registries.CONFIGURED_FEATURE, id("mining_landmark"));
    /** Placed-feature key for the legacy landmark feature. */
    public static final ResourceKey<PlacedFeature> MINING_LANDMARK_PLACED =
            ResourceKey.create(Registries.PLACED_FEATURE, id("mining_landmark"));

    private DelvefoldWorldgen() {}

    /**
     * Attaches Delvefold feature and placement-modifier registrations to the mod lifecycle bus.
     *
     * @param modEventBus mod-scoped NeoForge registration bus; call during mod construction
     */
    public static void register(IEventBus modEventBus) {
        FEATURES.register(modEventBus);
        PLACEMENT_MODIFIERS.register(modEventBus);
    }

    /**
     * Creates a namespaced Delvefold resource identifier.
     *
     * @param path registry path without a namespace
     * @return identifier in the {@code delvefold} namespace
     */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Delvefold.MOD_ID, path);
    }

    /**
     * Resolves the immutable dimension key for a terrain mode and generator variant.
     *
     * @param mode flat, cavern, or overworld-like terrain
     * @param variant classic or expansive generator variant
     * @return the stable level key assigned to that exact pair
     */
    public static ResourceKey<Level> levelFor(TerrainMode mode, TerrainVariant variant) {
        boolean expansive = variant == TerrainVariant.EXPANSIVE;
        return switch (mode) {
            case FLAT -> expansive ? FLAT_EXPANSIVE_LEVEL : FLAT_LEVEL;
            case CAVERN -> expansive ? CAVERN_EXPANSIVE_LEVEL : CAVERN_LEVEL;
            case WILD -> expansive ? WILD_EXPANSIVE_LEVEL : WILD_LEVEL;
        };
    }

    /**
     * Tests whether a level key is one of Delvefold's six mining dimensions.
     *
     * @param key level key to inspect
     * @return {@code true} for any classic or expansive Delvefold mining level
     */
    public static boolean isMiningLevel(ResourceKey<Level> key) {
        return key.equals(FLAT_LEVEL)
                || key.equals(CAVERN_LEVEL)
                || key.equals(WILD_LEVEL)
                || key.equals(FLAT_EXPANSIVE_LEVEL)
                || key.equals(CAVERN_EXPANSIVE_LEVEL)
                || key.equals(WILD_EXPANSIVE_LEVEL);
    }

    /**
     * Tests whether a level key uses either cavern generator.
     *
     * @param key level key to inspect
     * @return {@code true} only for classic or expansive cavern levels
     */
    public static boolean isCavernLevel(ResourceKey<Level> key) {
        return key.equals(CAVERN_LEVEL) || key.equals(CAVERN_EXPANSIVE_LEVEL);
    }

    /**
     * Maps a Delvefold level key back to its terrain mode.
     *
     * @param key level key to inspect
     * @return matching terrain mode, or {@code null} for a non-Delvefold level
     */
    public static @Nullable TerrainMode terrainFor(ResourceKey<Level> key) {
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

    /**
     * Maps a mining-biome holder back to its terrain mode.
     *
     * @param biome biome holder from the active generation registry
     * @return matching terrain mode, or {@code null} when the holder has no Delvefold mining-biome key
     */
    public static @Nullable TerrainMode terrainFor(Holder<Biome> biome) {
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

    /**
     * Resolves the mining-biome key assigned to a terrain mode.
     *
     * @param mode terrain mode to map
     * @return stable biome key for that mode
     */
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
