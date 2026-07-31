package com.nightsta69.delvefold.world;

import com.nightsta69.delvefold.Delvefold;
import com.nightsta69.delvefold.world.feature.MiningOreFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Static registrations and resource keys for Delvefold world generation.
 *
 * <p>The three terrain variants deliberately use separate level keys. A server may change which
 * one its portal targets without ever changing the generator attached to an existing level and
 * producing seams between old and new chunks.</p>
 */
public final class DelvefoldWorldgen {
    private static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, Delvefold.MOD_ID);

    public static final DeferredHolder<Feature<?>, MiningOreFeature> MINING_ORE_FEATURE =
            FEATURES.register("mining_ores", MiningOreFeature::new);

    public static final ResourceKey<Level> FLAT_LEVEL = levelKey("delve_flat");
    public static final ResourceKey<Level> CAVERN_LEVEL = levelKey("delve_cavern");
    public static final ResourceKey<Level> WILD_LEVEL = levelKey("delve_wild");

    public static final ResourceKey<LevelStem> FLAT_LEVEL_STEM = levelStemKey("delve_flat");
    public static final ResourceKey<LevelStem> CAVERN_LEVEL_STEM = levelStemKey("delve_cavern");
    public static final ResourceKey<LevelStem> WILD_LEVEL_STEM = levelStemKey("delve_wild");

    public static final ResourceKey<Biome> MINING_FLAT_BIOME = biomeKey("mining_flat");
    public static final ResourceKey<Biome> MINING_CAVERN_BIOME = biomeKey("mining_cavern");
    public static final ResourceKey<Biome> MINING_WILD_BIOME = biomeKey("mining_wild");

    public static final ResourceKey<ConfiguredFeature<?, ?>> MINING_ORES_CONFIGURED = ResourceKey.create(
            Registries.CONFIGURED_FEATURE, id("mining_ores"));
    public static final ResourceKey<PlacedFeature> MINING_ORES_PLACED = ResourceKey.create(
            Registries.PLACED_FEATURE, id("mining_ores"));

    private DelvefoldWorldgen() {
    }

    public static void register(IEventBus modEventBus) {
        FEATURES.register(modEventBus);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Delvefold.MOD_ID, path);
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
