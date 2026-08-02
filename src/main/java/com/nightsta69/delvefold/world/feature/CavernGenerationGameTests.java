package com.nightsta69.delvefold.world.feature;

import com.nightsta69.delvefold.Delvefold;
import com.nightsta69.delvefold.world.DelvefoldWorldgen;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.carver.CaveCarverConfiguration;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.DiskConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** GameTest coverage proving that Cavern generator and pocket resources decode into the live registries. */
@GameTestHolder(Delvefold.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CavernGenerationGameTests {
    private static final String EMPTY_TEMPLATE = "bastion/mobs/empty";
    private static final long[] SHAPE_PROBE_SEEDS = {0L, 0x5EEDC0DEL};
    private static final int[][] SHAPE_PROBE_COLUMNS = {{0, 0}, {1, -1}, {31, 47}, {-129, 257}, {4096, -3072}};

    private CavernGenerationGameTests() {}

    /**
     * Verifies that both dedicated Cavern settings load with the intended heights and without a global fluid table.
     *
     * @param helper NeoForge GameTest context used for registry access and assertions
     */
    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void dedicatedCavernNoiseSettingsDecodeWithDryStoneDefaults(GameTestHelper helper) {
        var registry = helper.getLevel().registryAccess().registryOrThrow(Registries.NOISE_SETTINGS);
        NoiseGeneratorSettings classic = registry.getOrThrow(DelvefoldWorldgen.CAVERN_NOISE_SETTINGS);
        NoiseGeneratorSettings expansive = registry.getOrThrow(DelvefoldWorldgen.CAVERN_EXPANSIVE_NOISE_SETTINGS);

        assertDryStoneSettings(helper, classic, 192, "Classic");
        assertDryStoneSettings(helper, expansive, 384, "Expansive");
        helper.succeed();
    }

    /**
     * Samples the real interpolated output of both base generators and proves that terrain begins as one continuous
     * solid host. Compact configured carvers are therefore the only source of Cavern voids and cannot leave remnants
     * from an amplified or cheese-noise terrain field.
     *
     * @param helper NeoForge GameTest context used for registry-backed worldgen sampling and assertions
     */
    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void cavernBaseColumnsAreSolidBeforeCompactCarving(GameTestHelper helper) {
        assertSolidBaseColumns(helper, DelvefoldWorldgen.CAVERN_NOISE_SETTINGS, 192, "Classic");
        assertSolidBaseColumns(helper, DelvefoldWorldgen.CAVERN_EXPANSIVE_NOISE_SETTINGS, 384, "Expansive");
        helper.succeed();
    }

    /**
     * Proves that the only Cavern carver resource resolves through the live datapack registry as the bounded cave type.
     *
     * @param helper NeoForge GameTest context used for live configured-carver registry access
     */
    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void compactCaveCarverDecodesFromData(GameTestHelper helper) {
        var configured = helper.getLevel()
                .registryAccess()
                .registryOrThrow(Registries.CONFIGURED_CARVER)
                .getOrThrow(DelvefoldWorldgen.COMPACT_CAVE_CARVER);
        helper.assertTrue(configured.worldCarver().equals(WorldCarver.CAVE), "Compact Cavern carver was not a cave");
        helper.assertTrue(
                configured.config() instanceof CaveCarverConfiguration,
                "Compact Cavern carver did not decode its cave configuration");
        helper.assertTrue(
                Math.abs(configured.config().probability - 0.18F) < 0.000001F,
                "Compact Cavern carver probability changed");
        helper.succeed();
    }

    /**
     * Verifies that the bounded water-pocket definition decodes as a shallow disk and its placement chain loads.
     *
     * @param helper NeoForge GameTest context used for registry access and assertions
     */
    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void boundedWaterPocketResourcesDecodeFromData(GameTestHelper helper) {
        var configuredRegistry = helper.getLevel().registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
        var placedRegistry = helper.getLevel().registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        ConfiguredFeature<?, ?> water = configuredRegistry.getOrThrow(DelvefoldWorldgen.CAVERN_WATER_POCKET_CONFIGURED);
        PlacedFeature placedWater = placedRegistry.getOrThrow(DelvefoldWorldgen.CAVERN_WATER_POCKET_PLACED);

        assertDisk(helper, water, Blocks.WATER, "Water");
        helper.assertTrue(
                placedWater.feature().is(DelvefoldWorldgen.CAVERN_WATER_POCKET_CONFIGURED),
                "Water placed feature did not reference its configured feature");
        helper.assertTrue(placedWater.placement().size() == 6, "Water pocket placement chain was incomplete");
        List<PlacementModifierType<?>> expectedOrder = List.of(
                PlacementModifierType.COUNT,
                PlacementModifierType.RARITY_FILTER,
                PlacementModifierType.IN_SQUARE,
                PlacementModifierType.HEIGHT_RANGE,
                PlacementModifierType.ENVIRONMENT_SCAN,
                PlacementModifierType.BIOME_FILTER);
        for (int index = 0; index < expectedOrder.size(); index++) {
            helper.assertTrue(
                    placedWater.placement().get(index).type().equals(expectedOrder.get(index)),
                    "Water pocket placement modifier was out of order at index " + index);
        }
        helper.succeed();
    }

    /**
     * Places the decoded disk on a controlled stone floor and proves that its one-block depth and exposed-floor
     * predicate prevent downward replacement and vertical-wall carving.
     *
     * @param helper NeoForge GameTest context used for registry access, fixture blocks, and assertions
     */
    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void waterPocketReplacesOnlyExposedFloorBlocks(GameTestHelper helper) {
        ConfiguredFeature<?, ?> water = helper.getLevel()
                .registryAccess()
                .registryOrThrow(Registries.CONFIGURED_FEATURE)
                .getOrThrow(DelvefoldWorldgen.CAVERN_WATER_POCKET_CONFIGURED);
        BlockPos center = new BlockPos(3, 2, 3);
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, 3, z), Blocks.AIR);
            }
        }
        BlockPos wallBase = center.offset(1, 0, 0);
        BlockPos wall = center.offset(1, 1, 0);
        helper.setBlock(wall, Blocks.STONE);

        boolean placed = water.place(
                helper.getLevel(),
                helper.getLevel().getChunkSource().getGenerator(),
                RandomSource.create(0xC0A7E42L),
                helper.absolutePos(center));

        helper.assertTrue(placed, "Water pocket did not place on a valid exposed stone floor");
        helper.assertBlockPresent(Blocks.WATER, center);
        helper.assertBlockPresent(Blocks.STONE, center.below());
        helper.assertBlockPresent(Blocks.STONE, wallBase);
        helper.assertBlockPresent(Blocks.STONE, wall);
        helper.succeed();
    }

    private static void assertDryStoneSettings(
            GameTestHelper helper, NoiseGeneratorSettings settings, int expectedHeight, String label) {
        helper.assertTrue(settings.defaultBlock().is(Blocks.STONE), label + " Cavern default block was not stone");
        helper.assertTrue(settings.defaultFluid().isAir(), label + " Cavern retained a global fluid table");
        helper.assertTrue(settings.seaLevel() == -64, label + " Cavern sea level was not below the build space");
        helper.assertTrue(!settings.isAquifersEnabled(), label + " Cavern aquifers remained enabled");
        helper.assertTrue(settings.noiseSettings().minY() == -64, label + " Cavern minimum Y changed");
        helper.assertTrue(
                settings.noiseSettings().height() == expectedHeight, label + " Cavern generator height changed");
    }

    private static void assertSolidBaseColumns(
            GameTestHelper helper, ResourceKey<NoiseGeneratorSettings> settingsKey, int expectedHeight, String label) {
        var registryAccess = helper.getLevel().registryAccess();
        var settings = registryAccess.registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(settingsKey);
        var cavernBiome = registryAccess
                .registryOrThrow(Registries.BIOME)
                .getHolderOrThrow(DelvefoldWorldgen.MINING_CAVERN_BIOME);
        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(new FixedBiomeSource(cavernBiome), settings);
        int minY = settings.value().noiseSettings().minY();
        LevelHeightAccessor bounds = LevelHeightAccessor.create(minY, expectedHeight);

        for (long seed : SHAPE_PROBE_SEEDS) {
            RandomState randomState = RandomState.create(registryAccess.asGetterLookup(), settingsKey, seed);
            for (int[] coordinates : SHAPE_PROBE_COLUMNS) {
                int x = coordinates[0];
                int z = coordinates[1];
                NoiseColumn column = generator.getBaseColumn(x, z, bounds, randomState);
                for (int y = minY; y < minY + expectedHeight; y++) {
                    var state = column.getBlock(y);
                    helper.assertTrue(
                            !state.isAir() && state.getFluidState().isEmpty(),
                            label + " Cavern base host was not solid at " + x + "," + y + "," + z + " for seed "
                                    + seed);
                }
            }
        }
    }

    private static void assertDisk(
            GameTestHelper helper,
            ConfiguredFeature<?, ?> configured,
            net.minecraft.world.level.block.Block fluid,
            String label) {
        helper.assertTrue(
                configured.feature().equals(Feature.DISK), label + " pocket was not decoded as a disk feature");
        helper.assertTrue(configured.config() instanceof DiskConfiguration, label + " pocket disk config was missing");
        DiskConfiguration disk = (DiskConfiguration) configured.config();
        helper.assertTrue(disk.halfHeight() == 0, label + " pocket was not one block deep");
        helper.assertTrue(disk.radius().getMinValue() == 1, label + " pocket minimum radius was not one block");
        helper.assertTrue(disk.radius().getMaxValue() == 2, label + " pocket maximum radius was not two blocks");
        helper.assertTrue(
                disk.stateProvider()
                        .fallback()
                        .getState(helper.getLevel().getRandom(), helper.absolutePos(net.minecraft.core.BlockPos.ZERO))
                        .is(fluid),
                label + " pocket fallback state decoded incorrectly");
    }
}
