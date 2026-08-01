package com.nightsta69.delvefold.world.feature;

import com.nightsta69.delvefold.Delvefold;
import com.nightsta69.delvefold.config.DelvefoldConfigService;
import com.nightsta69.delvefold.config.model.BiomeFilter;
import com.nightsta69.delvefold.config.model.GeologyTheme;
import com.nightsta69.delvefold.config.model.HeightDistribution;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.OreRule;
import com.nightsta69.delvefold.config.model.OreTarget;
import com.nightsta69.delvefold.config.model.ProvinceSettings;
import com.nightsta69.delvefold.config.model.SpawnBand;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.world.DelvefoldWorldgen;
import com.nightsta69.delvefold.world.feature.GeologyThemePlanner.Material;
import com.nightsta69.delvefold.world.feature.GeologyThemePlanner.Placement;
import com.nightsta69.delvefold.world.feature.GeologyThemePlanner.Position;
import com.nightsta69.delvefold.world.feature.GeologyThemePlanner.Role;
import com.nightsta69.delvefold.world.feature.ProvincePlacementPlanner.Candidate;
import com.nightsta69.delvefold.world.feature.ProvincePlacementPlanner.Plan;
import com.nightsta69.delvefold.world.feature.ProvincePlacementPlanner.ProvinceSlice;
import com.nightsta69.delvefold.world.feature.RuntimeOreProfile.CompiledBand;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Runtime adapter coverage for themed geology and regional ore provinces. */
@GameTestHolder(Delvefold.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LivingGeologyGameTests {
    private static final String RESERVATION_TEMPLATE = "gametest/living_geology_reservation";
    private static final long GENERATION_SALT = 0x71L;

    private LivingGeologyGameTests() {}

    /**
     * Verifies that themed-geology mutations remain inside the currently generating chunk, respect protected template
     * blocks, and leave the classic theme as a no-op.
     *
     * @param helper NeoForge GameTest context providing the reserved test world and assertions
     */
    @GameTest(templateNamespace = Delvefold.MOD_ID, template = RESERVATION_TEMPLATE)
    public static void geologyAdapterRejectsUnsafeMutationsAndClassicIsANoOp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = anchor(helper);
        ChunkPos chunk = new ChunkPos(anchor);
        int y = anchor.getY();
        int minimumY = y - 1;
        int maximumY = y + 1;
        BlockPos replaceable = interior(chunk, y, 5, 5);
        BlockPos playerBlock = interior(chunk, y, 8, 5);
        BlockPos outsideChunk = new BlockPos(chunk.getMaxBlockX() + 1, y, chunk.getMinBlockZ() + 5);
        BlockPos outsideHeight = interior(chunk, y + 2, 11, 5);
        set(level, replaceable, Blocks.STONE);
        set(level, playerBlock, Blocks.DIAMOND_BLOCK);
        set(level, outsideChunk, Blocks.STONE);
        set(level, outsideHeight, Blocks.STONE);

        int writes = GeologyThemeFeature.applyPlan(
                level,
                chunk,
                new GeologyThemePlanner.Plan(List.of(
                        placement(replaceable, Material.TUFF, Role.STRATA),
                        placement(playerBlock, Material.BASALT, Role.STRATA),
                        placement(outsideChunk, Material.BLACKSTONE, Role.STRATA),
                        placement(outsideHeight, Material.CALCITE, Role.STRATA))),
                TerrainMode.FLAT,
                minimumY,
                maximumY);

        helper.assertTrue(writes == 1, "The geology adapter accepted an unsafe mutation");
        helper.assertTrue(
                level.getBlockState(replaceable).is(Blocks.TUFF),
                "Natural stone was not replaced by the accepted strata placement");
        helper.assertTrue(
                level.getBlockState(playerBlock).is(Blocks.DIAMOND_BLOCK),
                "The geology adapter replaced a player/non-natural block");
        helper.assertTrue(
                level.getBlockState(outsideChunk).is(Blocks.STONE),
                "The geology adapter wrote outside the generating chunk");
        helper.assertTrue(
                level.getBlockState(outsideHeight).is(Blocks.STONE),
                "The geology adapter wrote outside its bounded vertical range");

        set(level, replaceable, Blocks.STONE);
        int classicWrites = GeologyThemeFeature.applyTheme(
                level,
                chunk,
                GeologyTheme.CLASSIC,
                GeologyThemeConfiguration.Phase.STRATA,
                TerrainMode.FLAT,
                level.getSeed(),
                GENERATION_SALT,
                minimumY,
                maximumY);
        helper.assertTrue(
                classicWrites == 0 && level.getBlockState(replaceable).is(Blocks.STONE),
                "Classic geology unexpectedly mutated the world");
        helper.succeed();
    }

    /**
     * Verifies that the compiled ore-profile cache is reused within one registry lifetime and is invalidated when a
     * datapack registry reload may change tag-derived output membership.
     *
     * @param helper NeoForge GameTest context used for assertions
     */
    @GameTest(templateNamespace = Delvefold.MOD_ID, template = RESERVATION_TEMPLATE)
    public static void runtimeOreProfileCacheInvalidatesAcrossRegistryReloads(GameTestHelper helper) {
        OreProfileDocument document = DelvefoldConfigService.get().snapshot().ores();
        MiningOreFeature feature = DelvefoldWorldgen.MINING_ORE_FEATURE.get();
        feature.invalidateRuntimeProfile();
        RuntimeOreProfile first = feature.profileFor(document);
        RuntimeOreProfile repeated = feature.profileFor(document);
        helper.assertTrue(first == repeated, "An unchanged ore profile did not reuse its compiled registry view");
        feature.invalidateRuntimeProfile();
        RuntimeOreProfile afterReload = feature.profileFor(document);
        helper.assertTrue(afterReload != first, "A registry reload retained tag-derived ore output choices");
        helper.succeed();
    }

    /**
     * Verifies that a geology fluid pocket is placed only when every neighbor forms a complete, natural, in-chunk seal
     * and is rejected when validation crosses a chunk boundary.
     *
     * @param helper NeoForge GameTest context providing the reserved test world and assertions
     */
    @GameTest(templateNamespace = Delvefold.MOD_ID, template = RESERVATION_TEMPLATE)
    public static void geologyFluidsRequireACompleteInChunkNaturalSeal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = anchor(helper);
        ChunkPos chunk = new ChunkPos(anchor);
        int y = anchor.getY();
        BlockPos sealed = interior(chunk, y, 4, 6);
        BlockPos exposed = interior(chunk, y, 10, 6);
        BlockPos edge = new BlockPos(chunk.getMaxBlockX(), y, chunk.getMinBlockZ() + 11);
        prepareNaturalSeal(level, sealed);
        prepareNaturalSeal(level, exposed);
        prepareNaturalSeal(level, edge);
        set(level, exposed.east(), Blocks.AIR);

        int writes = GeologyThemeFeature.applyPlan(
                level,
                chunk,
                new GeologyThemePlanner.Plan(List.of(
                        placement(sealed, Material.WATER, Role.FLUID),
                        placement(exposed, Material.LAVA, Role.FLUID),
                        placement(edge, Material.LAVA, Role.FLUID))),
                TerrainMode.CAVERN,
                y - 2,
                y + 2);

        helper.assertTrue(writes == 1, "An exposed or cross-chunk fluid pocket was accepted");
        helper.assertTrue(
                level.getBlockState(sealed).is(Blocks.WATER),
                "A completely sealed natural pocket did not receive its fluid");
        helper.assertTrue(level.getBlockState(exposed).is(Blocks.STONE), "An air-exposed fluid pocket was placed");
        helper.assertTrue(
                level.getBlockState(edge).is(Blocks.STONE),
                "A fluid pocket whose seal crossed the chunk boundary was placed");
        helper.succeed();
    }

    /**
     * Verifies that province placement replaces eligible host blocks while every write remains clipped to the currently
     * processed 16-by-16-block chunk.
     *
     * @param helper NeoForge GameTest context providing the reserved test world and assertions
     */
    @GameTest(templateNamespace = Delvefold.MOD_ID, template = RESERVATION_TEMPLATE)
    public static void provinceAdapterReplacesHostsWithoutWritingOutsideItsChunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = anchor(helper);
        ChunkPos chunk = new ChunkPos(anchor);
        int y = anchor.getY();
        CompiledBand band = compileProvinceBand(
                helper,
                "chunk_safety",
                List.of(OreTarget.of("minecraft:diamond_ore", "minecraft:stone_ore_replaceables")),
                0.0D,
                y,
                new ProvinceSettings(16, 16, 1, 1.0D, 512));
        Plan plan = plan(level, chunk, band);
        Set<BlockPos> candidates = positions(plan);
        helper.assertTrue(!candidates.isEmpty(), "The deterministic province produced no test candidates");
        candidates.forEach(position -> set(level, position, Blocks.STONE));
        BlockPos outside = new BlockPos(chunk.getMaxBlockX() + 1, y, chunk.getMinBlockZ() + 8);
        set(level, outside, Blocks.STONE);

        boolean placed = MiningOreFeature.placeProvinceBand(level, chunk, band, level.getSeed(), GENERATION_SALT);

        helper.assertTrue(placed, "The province runtime adapter did not replace any configured host");
        helper.assertTrue(
                candidates.stream()
                        .allMatch(position -> level.getBlockState(position).is(Blocks.DIAMOND_ORE)),
                "A valid stone host was not replaced with the configured province output");
        helper.assertTrue(
                level.getBlockState(outside).is(Blocks.STONE),
                "The province runtime adapter wrote into an adjacent chunk");

        ProvinceSlice source = plan.provinces().getFirst();
        Candidate malicious = new Candidate(outside.getX(), outside.getY(), outside.getZ(), 1234L);
        Plan maliciousPlan = only(source, List.of(malicious));
        helper.assertTrue(
                !MiningOreFeature.applyProvincePlan(level, chunk, band, maliciousPlan),
                "A plan containing only an out-of-chunk candidate reported a write");
        helper.assertTrue(
                level.getBlockState(outside).is(Blocks.STONE),
                "The final mutation guard accepted an out-of-chunk province candidate");
        helper.succeed();
    }

    /**
     * Verifies that adjacent chunks derive the same province and canonical output block from the shared regional seed
     * while repeated generation of either chunk is deterministic.
     *
     * @param helper NeoForge GameTest context providing the reserved test world and assertions
     */
    @SuppressWarnings("ReferenceEquality")
    @GameTest(templateNamespace = Delvefold.MOD_ID, template = RESERVATION_TEMPLATE)
    public static void adjacentChunksUseTheSameDeterministicProvinceOutput(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = anchor(helper);
        ChunkPos west = new ChunkPos(anchor);
        ChunkPos east = new ChunkPos(west.x + 1, west.z);
        int y = anchor.getY();
        CompiledBand band = compileProvinceBand(
                helper,
                "shared_output",
                List.of(
                        OreTarget.of("minecraft:diamond_ore", "minecraft:stone_ore_replaceables"),
                        OreTarget.of("minecraft:emerald_ore", "minecraft:stone_ore_replaceables")),
                0.0D,
                y,
                new ProvinceSettings(16, 16, 1, 1.0D, 512));
        SlicePair shared = sharedSlices(plan(level, west, band), plan(level, east, band), 1, 0, helper);
        helper.assertTrue(
                shared.west().outputSeed() == shared.east().outputSeed(),
                "Adjacent chunks disagreed on the shared province output seed");
        setCandidates(level, shared.west().candidates(), Blocks.STONE);
        setCandidates(level, shared.east().candidates(), Blocks.STONE);

        helper.assertTrue(
                MiningOreFeature.applyProvincePlan(
                        level, west, band, only(shared.west(), shared.west().candidates())),
                "The west half of the shared province did not place");
        helper.assertTrue(
                MiningOreFeature.applyProvincePlan(
                        level, east, band, only(shared.east(), shared.east().candidates())),
                "The east half of the shared province did not place");
        Block selected =
                level.getBlockState(pos(shared.west().candidates().getFirst())).getBlock();
        helper.assertTrue(
                selected == Blocks.DIAMOND_ORE || selected == Blocks.EMERALD_ORE,
                "The shared province selected an unexpected output");
        assertAll(
                helper,
                level,
                shared.west().candidates(),
                selected,
                "The west chunk mixed outputs inside one province");
        assertAll(
                helper,
                level,
                shared.east().candidates(),
                selected,
                "Adjacent chunks disagreed on the shared province output");

        setCandidates(level, shared.west().candidates(), Blocks.STONE);
        setCandidates(level, shared.east().candidates(), Blocks.STONE);
        MiningOreFeature.applyProvincePlan(
                level, west, band, only(shared.west(), shared.west().candidates()));
        MiningOreFeature.applyProvincePlan(
                level, east, band, only(shared.east(), shared.east().candidates()));
        assertAll(
                helper,
                level,
                shared.west().candidates(),
                selected,
                "Repeating the west chunk changed its deterministic output");
        assertAll(
                helper,
                level,
                shared.east().candidates(),
                selected,
                "Repeating the east chunk changed its deterministic output");
        helper.succeed();
    }

    /**
     * Verifies deterministic air-exposure filtering on both sides of a chunk border without allowing either chunk pass
     * to write into its neighbor.
     *
     * @param helper NeoForge GameTest context providing the reserved test world and assertions
     */
    @GameTest(templateNamespace = Delvefold.MOD_ID, template = RESERVATION_TEMPLATE)
    public static void adjacentProvinceChunksApplyAirExposureDeterministically(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = anchor(helper);
        ChunkPos west = new ChunkPos(anchor);
        ChunkPos east = new ChunkPos(west.x + 1, west.z);
        int y = anchor.getY();
        CompiledBand band = compileProvinceBand(
                helper,
                "air_exposure",
                List.of(OreTarget.of("minecraft:diamond_ore", "minecraft:stone_ore_replaceables")),
                1.0D,
                y,
                new ProvinceSettings(16, 16, 1, 1.0D, 512));
        SlicePair shared = sharedSlices(plan(level, west, band), plan(level, east, band), 2, 4, helper);
        CandidatePair westCandidates = separatedCandidates(shared.west(), helper);
        CandidatePair eastCandidates = separatedCandidates(shared.east(), helper);

        for (int run = 0; run < 2; run++) {
            prepareAirPair(level, westCandidates);
            prepareAirPair(level, eastCandidates);
            MiningOreFeature.applyProvincePlan(level, west, band, only(shared.west(), westCandidates.asList()));
            MiningOreFeature.applyProvincePlan(level, east, band, only(shared.east(), eastCandidates.asList()));
            assertAirPair(helper, level, westCandidates, "west", run);
            assertAirPair(helper, level, eastCandidates, "east", run);
        }
        helper.succeed();
    }

    private static CompiledBand compileProvinceBand(
            GameTestHelper helper,
            String ruleId,
            List<OreTarget> targets,
            double discardOnAirExposure,
            int y,
            ProvinceSettings settings) {
        SpawnBand spawnBand = SpawnBand.province(
                "main", HeightDistribution.UNIFORM, y, y, null, null, null, discardOnAirExposure, settings);
        OreRule rule = new OreRule(
                ruleId,
                true,
                true,
                Set.of(TerrainMode.FLAT),
                targets,
                new BiomeFilter(List.of(), List.of()),
                List.of(spawnBand));
        RuntimeOreProfile profile = RuntimeOreProfile.compile(new OreProfileDocument(
                OreProfileDocument.CURRENT_SCHEMA_VERSION, 0L, "living_geology_test", List.of(rule)));
        return profile.bands(TerrainMode.FLAT, helper.getLevel().getBiome(anchor(helper)))
                .getFirst();
    }

    private static Plan plan(ServerLevel level, ChunkPos chunk, CompiledBand band) {
        return ProvincePlacementPlanner.plan(
                level.getSeed(),
                GENERATION_SALT,
                band.salt(),
                band.sourceBand(),
                chunk.x,
                chunk.z,
                level.getMinBuildHeight(),
                level.getMaxBuildHeight());
    }

    private static Plan only(ProvinceSlice source, List<Candidate> candidates) {
        ProvinceSlice slice = new ProvinceSlice(
                source.regionX(),
                source.regionZ(),
                source.centerX(),
                source.centerY(),
                source.centerZ(),
                source.outputSeed(),
                candidates);
        return new Plan(List.of(slice), candidates.size());
    }

    private static SlicePair sharedSlices(
            Plan west, Plan east, int minimumCandidates, int minimumSeparation, GameTestHelper helper) {
        Map<String, ProvinceSlice> eastByRegion = new HashMap<>();
        for (ProvinceSlice slice : east.provinces()) {
            eastByRegion.put(regionKey(slice), slice);
        }
        for (ProvinceSlice westSlice : west.provinces()) {
            ProvinceSlice eastSlice = eastByRegion.get(regionKey(westSlice));
            if (eastSlice != null
                    && westSlice.candidates().size() >= minimumCandidates
                    && eastSlice.candidates().size() >= minimumCandidates
                    && hasSeparatedCandidates(westSlice, minimumSeparation)
                    && hasSeparatedCandidates(eastSlice, minimumSeparation)) {
                return new SlicePair(westSlice, eastSlice);
            }
        }
        helper.assertTrue(false, "No deterministic province crossed the adjacent test chunks");
        throw new IllegalStateException("Unreachable after GameTest assertion");
    }

    private static CandidatePair separatedCandidates(ProvinceSlice slice, GameTestHelper helper) {
        for (int first = 0; first < slice.candidates().size(); first++) {
            for (int second = first + 1; second < slice.candidates().size(); second++) {
                Candidate exposed = slice.candidates().get(first);
                Candidate sealed = slice.candidates().get(second);
                if (distance(exposed, sealed) >= 4) {
                    return new CandidatePair(exposed, sealed);
                }
            }
        }
        helper.assertTrue(false, "The shared province did not provide separated air-test candidates");
        throw new IllegalStateException("Unreachable after GameTest assertion");
    }

    private static boolean hasSeparatedCandidates(ProvinceSlice slice, int minimumSeparation) {
        if (minimumSeparation <= 0) {
            return true;
        }
        for (int first = 0; first < slice.candidates().size(); first++) {
            for (int second = first + 1; second < slice.candidates().size(); second++) {
                if (distance(slice.candidates().get(first), slice.candidates().get(second)) >= minimumSeparation) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int distance(Candidate first, Candidate second) {
        return Math.abs(first.x() - second.x()) + Math.abs(first.y() - second.y()) + Math.abs(first.z() - second.z());
    }

    private static void prepareAirPair(ServerLevel level, CandidatePair pair) {
        BlockPos exposed = pos(pair.exposed());
        BlockPos sealed = pos(pair.sealed());
        set(level, exposed, Blocks.STONE);
        set(level, exposed.above(), Blocks.AIR);
        set(level, sealed, Blocks.STONE);
        for (Direction direction : Direction.values()) {
            set(level, sealed.relative(direction), Blocks.BEDROCK);
        }
        set(level, sealed, Blocks.STONE);
    }

    private static void assertAirPair(
            GameTestHelper helper, ServerLevel level, CandidatePair pair, String side, int run) {
        helper.assertTrue(
                level.getBlockState(pos(pair.exposed())).is(Blocks.STONE),
                "The air-exposed " + side + " candidate placed on run " + run);
        helper.assertTrue(
                level.getBlockState(pos(pair.sealed())).is(Blocks.DIAMOND_ORE),
                "The sealed " + side + " candidate did not place on run " + run);
    }

    private static void assertAll(
            GameTestHelper helper, ServerLevel level, List<Candidate> candidates, Block expected, String message) {
        helper.assertTrue(
                candidates.stream()
                        .allMatch(
                                candidate -> level.getBlockState(pos(candidate)).is(expected)),
                message);
    }

    private static Set<BlockPos> positions(Plan plan) {
        Set<BlockPos> positions = new LinkedHashSet<>();
        for (ProvinceSlice province : plan.provinces()) {
            province.candidates().stream().map(LivingGeologyGameTests::pos).forEach(positions::add);
        }
        return positions;
    }

    private static void setCandidates(ServerLevel level, List<Candidate> candidates, Block block) {
        candidates.forEach(candidate -> set(level, pos(candidate), block));
    }

    private static void prepareNaturalSeal(ServerLevel level, BlockPos target) {
        set(level, target, Blocks.STONE);
        for (Direction direction : Direction.values()) {
            set(level, target.relative(direction), Blocks.STONE);
        }
    }

    private static Placement placement(BlockPos position, Material material, Role role) {
        return new Placement(new Position(position.getX(), position.getY(), position.getZ()), material, role);
    }

    private static BlockPos interior(ChunkPos chunk, int y, int localX, int localZ) {
        return new BlockPos(chunk.getMinBlockX() + localX, y, chunk.getMinBlockZ() + localZ);
    }

    private static BlockPos anchor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos raw = helper.absolutePos(new BlockPos(17, 4, 17));
        int y = Math.max(level.getMinBuildHeight() + 8, Math.min(level.getMaxBuildHeight() - 9, raw.getY()));
        return new BlockPos(raw.getX(), y, raw.getZ());
    }

    private static BlockPos pos(Candidate candidate) {
        return new BlockPos(candidate.x(), candidate.y(), candidate.z());
    }

    private static String regionKey(ProvinceSlice slice) {
        return slice.regionX() + ":" + slice.regionZ();
    }

    private static void set(ServerLevel level, BlockPos position, Block block) {
        level.setBlock(position, block.defaultBlockState(), 3);
    }

    private record SlicePair(ProvinceSlice west, ProvinceSlice east) {}

    private record CandidatePair(Candidate exposed, Candidate sealed) {
        private List<Candidate> asList() {
            return List.of(exposed, sealed);
        }
    }
}
