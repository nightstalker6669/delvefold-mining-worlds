package com.nightsta69.delvefold.world.landmark;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.nightsta69.delvefold.Delvefold;
import com.nightsta69.delvefold.config.model.LandmarkPreset;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.TerrainVariant;
import com.nightsta69.delvefold.config.model.WorldIdentitySettings;
import com.nightsta69.delvefold.world.landmark.catalog.LandmarkBiomeSelectors;
import com.nightsta69.delvefold.world.landmark.catalog.LandmarkCatalogService;
import com.nightsta69.delvefold.world.landmark.catalog.LandmarkCatalogSnapshot;
import com.nightsta69.delvefold.world.landmark.catalog.LandmarkCategory;
import com.nightsta69.delvefold.world.landmark.catalog.LandmarkDefinition;
import com.nightsta69.delvefold.world.landmark.catalog.LandmarkPlacementStyle;
import com.nightsta69.delvefold.world.landmark.catalog.LandmarkSelector;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.EnumSet;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Delvefold.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LandmarkGameTests {
    private static final String EMPTY_TEMPLATE = "bastion/mobs/empty";
    private static final List<String> BUNDLED_IDS = List.of(
            "collapsed_mine_entrance", "fault_line_grotto", "geode_vault",
            "lift_station", "motherlode_chamber", "survey_camp");

    private LandmarkGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void catalogCodecLoadsEveryBundledDefinition(GameTestHelper helper) throws Exception {
        EnumMap<TerrainMode, Integer> terrainCoverage = new EnumMap<>(TerrainMode.class);
        for (TerrainMode terrain : TerrainMode.values()) {
            terrainCoverage.put(terrain, 0);
        }
        for (String id : BUNDLED_IDS) {
            String path = "/data/delvefold/delvefold/landmarks/" + id + ".json";
            try (var input = LandmarkGameTests.class.getResourceAsStream(path)) {
                helper.assertTrue(input != null, "Missing catalog resource " + path);
                JsonElement json = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8));
                LandmarkDefinition.Body body = LandmarkDefinition.BODY_CODEC.parse(JsonOps.INSTANCE, json)
                        .getOrThrow(message -> new AssertionError(id + ": " + message));
                LandmarkDefinition definition = LandmarkDefinition.from(
                        ResourceLocation.fromNamespaceAndPath(Delvefold.MOD_ID, id), body);
                helper.assertTrue(!definition.terrainModes().isEmpty(), id + " had no terrain modes");
                helper.assertTrue(definition.minY() <= definition.maxY(), id + " inverted its height bounds");
                JsonElement encoded = LandmarkDefinition.BODY_CODEC.encodeStart(JsonOps.INSTANCE, body)
                        .getOrThrow(message -> new AssertionError(id + ": " + message));
                helper.assertTrue(encoded.getAsJsonObject().has("placement_style"),
                        id + " did not round-trip as a flat document");
                definition.terrainModes().forEach(terrain ->
                        terrainCoverage.compute(terrain, (ignored, count) -> count == null ? 1 : count + 1));
            }
        }
        HashSet<ResourceLocation> dimensionIds = new HashSet<>();
        for (TerrainMode terrain : TerrainMode.values()) {
            helper.assertTrue(terrainCoverage.get(terrain) > 0,
                    "Bundled catalog has no definition for " + terrain.serializedName());
            for (TerrainVariant variant : TerrainVariant.values()) {
                var levelKey = com.nightsta69.delvefold.world.DelvefoldWorldgen.levelFor(terrain, variant);
                helper.assertTrue(dimensionIds.add(levelKey.location()),
                        "Terrain variants share a dimension ID: " + levelKey.location());
                String path = "/data/delvefold/dimension/" + levelKey.location().getPath() + ".json";
                try (var input = LandmarkGameTests.class.getResourceAsStream(path)) {
                    helper.assertTrue(input != null, "Missing terrain variant resource " + path);
                    var dimension = JsonParser.parseReader(
                            new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
                    helper.assertTrue(dimension.has("type") && dimension.has("generator")
                                    && dimension.getAsJsonObject("generator").has("type"),
                            "Incomplete terrain variant resource " + path);
                }
            }
        }
        helper.assertTrue(dimensionIds.size() == TerrainMode.values().length * TerrainVariant.values().length,
                "Not all six terrain variants have independent dimension resources");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void invalidCatalogReloadRetainsTheExactLastKnownGoodSnapshot(GameTestHelper helper) {
        LandmarkCatalogService service = LandmarkCatalogService.get();
        LandmarkCatalogSnapshot serverCatalog = service.snapshot();
        service.reset();
        LandmarkDefinition original = definition("original", 1, LandmarkCategory.SURVEY_STATION);
        var accepted = service.publish(Map.of(original.id(), original), List.of(), Instant.EPOCH);
        LandmarkCatalogSnapshot lastGood = accepted.activeSnapshot();
        var rejected = service.publish(Map.of(), List.of("missing template"), Instant.EPOCH.plusSeconds(1));

        helper.assertTrue(accepted.applied(), "Valid catalog was rejected");
        helper.assertTrue(!rejected.applied(), "Invalid catalog was applied");
        helper.assertTrue(rejected.activeSnapshot() == lastGood, "Reload did not retain the exact snapshot");
        helper.assertTrue(rejected.diagnostics().retainingLastGood(), "Diagnostics omitted retention state");
        service.reset();
        if (!serverCatalog.definitions().isEmpty()) {
            service.publish(serverCatalog.definitions(), List.of(), serverCatalog.loadedAt());
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void landmarkPresetAcceptanceAndSelectionAreDeterministic(GameTestHelper helper) {
        long[] seedForBucket = {0L, 1L, 2L, 9L};
        int balanced = 0;
        int abundant = 0;
        for (long seed : seedForBucket) {
            balanced += LandmarkSelector.accepts(LandmarkPreset.BALANCED, seed) ? 1 : 0;
            abundant += LandmarkSelector.accepts(LandmarkPreset.ABUNDANT, seed) ? 1 : 0;
            helper.assertTrue(!LandmarkSelector.accepts(LandmarkPreset.PURE_MINING, seed),
                    "Pure Mining accepted a candidate");
        }
        helper.assertTrue(balanced == 1, "Balanced did not accept exactly 25% of deterministic buckets");
        helper.assertTrue(abundant == 3, "Abundant did not accept exactly 75% of deterministic buckets");

        LandmarkDefinition survey = definition("survey", 1, LandmarkCategory.SURVEY_STATION);
        LandmarkDefinition motherlode = definition("motherlode", 100, LandmarkCategory.MOTHERLODE);
        LandmarkCatalogSnapshot snapshot = new LandmarkCatalogSnapshot(1L,
                Map.of(survey.id(), survey, motherlode.id(), motherlode), Instant.EPOCH);
        WorldIdentitySettings surveyOnly = WorldIdentitySettings.defaults()
                .withLandmarks(LandmarkPreset.BALANCED, true, false, false);
        var first = LandmarkSelector.select(snapshot, TerrainMode.FLAT, surveyOnly, ignored -> true, 99L);
        var repeated = LandmarkSelector.select(snapshot, TerrainMode.FLAT, surveyOnly, ignored -> true, 99L);
        helper.assertTrue(first.equals(repeated) && first.orElseThrow() == survey,
                "Selection was not deterministic or ignored category toggles");
        helper.assertTrue(LandmarkSelector.categoryEnabled(LandmarkCategory.SURVEY_STATION, surveyOnly),
                "Survey toggle did not enable survey landmarks");
        helper.assertTrue(!LandmarkSelector.categoryEnabled(LandmarkCategory.MOTHERLODE, surveyOnly)
                        && !LandmarkSelector.categoryEnabled(LandmarkCategory.FAULT_LINE, surveyOnly),
                "Disabled category toggles still accepted landmarks");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void structureCatalogAndTemplatesLoadThroughVanillaRegistries(GameTestHelper helper) {
        Structure structure = helper.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE)
                .get(ResourceLocation.fromNamespaceAndPath(Delvefold.MOD_ID, "mining_landmark"));
        helper.assertTrue(structure instanceof DelvefoldLandmarkStructure,
                "Bundled structure did not load with Delvefold's structure type");
        StructureSet set = helper.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE_SET)
                .get(ResourceLocation.fromNamespaceAndPath(Delvefold.MOD_ID, "mining_landmarks"));
        helper.assertTrue(set != null, "Bundled landmark structure set was not registered");
        helper.assertTrue(set.placement() instanceof GenerationSaltedRandomSpreadPlacement,
                "Structure set did not load the generation-salted placement codec");
        GenerationSaltedRandomSpreadPlacement placement =
                (GenerationSaltedRandomSpreadPlacement) set.placement();
        helper.assertTrue(placement.spacing() == 12 && placement.separation() == 6,
                "Bundled structure spacing did not decode as 12/6");
        java.util.ArrayList<ChunkPos> stableCandidates = new java.util.ArrayList<>();
        java.util.ArrayList<ChunkPos> rotatedCandidates = new java.util.ArrayList<>();
        for (int region = -12; region <= 12; region++) {
            ChunkPos stable = placement.potentialStructureChunk(0x5EEDL, 0L, region * placement.spacing(), 0);
            ChunkPos rotated = placement.potentialStructureChunk(0x5EEDL, 71L, region * placement.spacing(), 0);
            helper.assertTrue(Math.floorDiv(stable.x, placement.spacing()) == region,
                    "Stable candidate escaped its random-spread region " + region);
            stableCandidates.add(stable);
            rotatedCandidates.add(rotated);
        }
        helper.assertTrue(stableCandidates.stream().distinct().count() == stableCandidates.size(),
                "Random-spread regions produced duplicate structure candidates");
        helper.assertTrue(!stableCandidates.equals(rotatedCandidates),
                "Generation salt did not rotate structure spacing candidates");
        boolean multiChunkTemplate = false;
        for (String id : BUNDLED_IDS) {
            var template = helper.getLevel().getStructureManager().get(
                    ResourceLocation.fromNamespaceAndPath(Delvefold.MOD_ID, "landmarks/" + id));
            helper.assertTrue(template.isPresent(), "Missing structure template " + id);
            helper.assertTrue(template.orElseThrow().getSize().getX()
                            <= LandmarkDefinition.MAX_TEMPLATE_HORIZONTAL_SPAN
                            && template.orElseThrow().getSize().getZ()
                            <= LandmarkDefinition.MAX_TEMPLATE_HORIZONTAL_SPAN,
                    id + " exceeds the placement scan bound");
            multiChunkTemplate |= template.orElseThrow().getSize().getX() > 16
                    || template.orElseThrow().getSize().getZ() > 16;
        }
        helper.assertTrue(multiChunkTemplate,
                "Bundled templates do not exercise structure-system multi-chunk placement");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void rotationsKeepEveryBundledTemplateCenteredOnItsCandidate(GameTestHelper helper) {
        int centerX = helper.absolutePos(new BlockPos(8, 1, 8)).getX();
        int centerZ = helper.absolutePos(new BlockPos(8, 1, 8)).getZ();
        int minimumY = helper.absolutePos(new BlockPos(0, 2, 0)).getY();
        for (String id : BUNDLED_IDS) {
            var template = helper.getLevel().getStructureManager().get(
                    ResourceLocation.fromNamespaceAndPath(Delvefold.MOD_ID, "landmarks/" + id)).orElseThrow();
            for (Rotation rotation : Rotation.values()) {
                BlockPos origin = DelvefoldLandmarkStructure.centeredOrigin(
                        template, rotation, centerX, minimumY, centerZ);
                var bounds = template.getBoundingBox(origin, rotation, BlockPos.ZERO, Mirror.NONE);
                helper.assertTrue(bounds.getCenter().getX() == centerX
                                && bounds.getCenter().getZ() == centerZ
                                && bounds.minY() == minimumY,
                        id + " shifted away from its candidate at rotation " + rotation
                                + ": " + bounds);
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void fixedMiningBiomesResolveToTheirOwnTerrain(GameTestHelper helper) {
        var biomes = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        for (TerrainMode terrain : TerrainMode.values()) {
            var biome = biomes.getHolderOrThrow(
                    com.nightsta69.delvefold.world.DelvefoldWorldgen.biomeFor(terrain));
            helper.assertTrue(com.nightsta69.delvefold.world.DelvefoldWorldgen.terrainFor(biome) == terrain,
                    "The " + terrain.serializedName() + " generator biome resolved as another terrain");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void capturedCatalogRevisionAndPlacementProbeCacheRemainCoherent(GameTestHelper helper) {
        LandmarkDefinition revisionOne = definition(
                "shared", "template_v1", LandmarkPlacementStyle.SURFACE, -32, 96);
        LandmarkDefinition revisionTwo = definition(
                "shared", "template_v2", LandmarkPlacementStyle.BURIED, -4, 4);
        LandmarkCatalogSnapshot first = new LandmarkCatalogSnapshot(
                1L, Map.of(revisionOne.id(), revisionOne), Instant.EPOCH);
        LandmarkCatalogSnapshot second = new LandmarkCatalogSnapshot(
                2L, Map.of(revisionTwo.id(), revisionTwo), Instant.EPOCH.plusSeconds(1));
        AtomicReference<LandmarkCatalogSnapshot> published = new AtomicReference<>(first);

        LandmarkCatalogSnapshot captured = published.get();
        var selected = LandmarkGenerationPlanner.select(
                captured,
                TerrainMode.FLAT,
                WorldIdentitySettings.defaults().withLandmarks(
                        LandmarkPreset.ABUNDANT, true, true, true),
                definition -> {
                    published.set(second);
                    return Optional.of(definition.template().toString());
                },
                17L).orElseThrow();
        helper.assertTrue(selected.definition() == revisionOne,
                "A catalog reload changed the selected definition after revision capture");
        helper.assertTrue("test:template_v1".equals(selected.candidate()),
                "A candidate from one catalog revision was paired with another revision");
        helper.assertTrue(published.get() == second,
                "The regression did not simulate publication during candidate resolution");

        LandmarkPlacementProbeCache cache = new LandmarkPlacementProbeCache();
        LandmarkDefinition surfaceA = definition(
                "surface_a", "surface_a", LandmarkPlacementStyle.SURFACE, -32, 96);
        LandmarkDefinition surfaceB = definition(
                "surface_b", "surface_b", LandmarkPlacementStyle.SURFACE, -32, 96);
        AtomicInteger surfaceCalls = new AtomicInteger();
        int firstHeight = cache.resolve(surfaceA, () -> {
            surfaceCalls.incrementAndGet();
            return 73;
        });
        int repeatedHeight = cache.resolve(surfaceB, () -> {
            surfaceCalls.incrementAndGet();
            return 99;
        });
        helper.assertTrue(firstHeight == 73 && repeatedHeight == 73
                        && surfaceCalls.get() == 1 && cache.cachedProbeCount() == 1,
                "Definition-independent surface probes were not safely shared");

        LandmarkDefinition buried = definition(
                "buried", "buried", LandmarkPlacementStyle.BURIED, -32, 96);
        AtomicInteger buriedCalls = new AtomicInteger();
        helper.assertTrue(cache.resolve(buried, buriedCalls::incrementAndGet) == 1
                        && cache.resolve(buried, buriedCalls::incrementAndGet) == 2
                        && buriedCalls.get() == 2,
                "Definition-dependent buried placement was incorrectly cached");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void lootMarkersInstallTheirTableOnlyOnce(GameTestHelper helper) {
        BlockPos position = helper.absolutePos(new BlockPos(3, 2, 3));
        helper.getLevel().setBlockAndUpdate(position, Blocks.BARREL.defaultBlockState());
        ResourceKey<LootTable> loot = ResourceKey.create(Registries.LOOT_TABLE,
                ResourceLocation.fromNamespaceAndPath(Delvefold.MOD_ID, "chests/survey_camp"));
        helper.assertTrue(LandmarkTemplatePiece.installLootOnce(helper.getLevel(), position, loot, 12345L),
                "First loot-marker application failed");
        helper.assertTrue(!LandmarkTemplatePiece.installLootOnce(helper.getLevel(), position, loot, 67890L),
                "Second loot-marker application overwrote the container");
        RandomizableContainer container = (RandomizableContainer) helper.getLevel().getBlockEntity(position);
        helper.assertTrue(loot.equals(container.getLootTable()) && container.getLootTableSeed() == 12345L,
                "The original one-time loot assignment was not preserved");
        BlockEntity blockEntity = (BlockEntity) container;
        container.setLootTable(null);
        container.setLootTableSeed(0L);
        container.clearContent();
        blockEntity.setChanged();
        helper.assertTrue(!LandmarkTemplatePiece.installLootOnce(helper.getLevel(), position, loot, 24680L),
                "An emptied landmark container was eligible for a second loot roll");
        helper.assertTrue(container.getLootTable() == null && container.isEmpty(),
                "Reapplying an emptied landmark marker restored its loot");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void persistedGenerationSaltRotatesIndependentLandmarkDomains(GameTestHelper helper) {
        long worldSeed = 0x1234_5678_9ABCDEFL;
        ChunkPos chunk = new ChunkPos(-17, 42);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Delvefold.MOD_ID, "survey_camp");
        helper.assertTrue(LandmarkSeeds.placementWorldSeed(worldSeed, 0L) == worldSeed,
                "Stable mode changed the compatible placement seed");
        helper.assertTrue(LandmarkSeeds.selectionSeed(worldSeed, chunk, 0L)
                        == LandmarkSeeds.selectionSeed(worldSeed, chunk, 0L),
                "Stable selection did not repeat");
        helper.assertTrue(LandmarkSeeds.placementWorldSeed(worldSeed, 0L)
                        != LandmarkSeeds.placementWorldSeed(worldSeed, 99L),
                "Generation salt did not rotate placement");
        helper.assertTrue(LandmarkSeeds.selectionSeed(worldSeed, chunk, 0L)
                        != LandmarkSeeds.selectionSeed(worldSeed, chunk, 99L),
                "Generation salt did not rotate selection");
        helper.assertTrue(LandmarkSeeds.definitionSeed(worldSeed, chunk, 0L, id)
                        != LandmarkSeeds.definitionSeed(worldSeed, chunk, 99L, id),
                "Generation salt did not rotate content");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void discoveryVisitTransitionsDeduplicateAndAdvancementIsServerAuthorized(GameTestHelper helper) {
        LandmarkDiscoveryService.Visit camp = new LandmarkDiscoveryService.Visit(
                helper.getLevel().dimension(), new ChunkPos(4, -7),
                ResourceLocation.fromNamespaceAndPath(Delvefold.MOD_ID, "survey_camp"));
        LandmarkDiscoveryService.Visit sameCamp = new LandmarkDiscoveryService.Visit(
                helper.getLevel().dimension(), new ChunkPos(4, -7),
                ResourceLocation.fromNamespaceAndPath(Delvefold.MOD_ID, "survey_camp"));
        LandmarkDiscoveryService.Visit grotto = new LandmarkDiscoveryService.Visit(
                helper.getLevel().dimension(), new ChunkPos(5, -7),
                ResourceLocation.fromNamespaceAndPath(Delvefold.MOD_ID, "fault_line_grotto"));

        helper.assertTrue(LandmarkDiscoveryService.isVisitTransition(null, camp),
                "Entering a landmark after no visit did not trigger discovery");
        helper.assertTrue(!LandmarkDiscoveryService.isVisitTransition(camp, sameCamp),
                "A continuous visit triggered discovery twice");
        helper.assertTrue(LandmarkDiscoveryService.isVisitTransition(camp, grotto),
                "Moving into a different landmark did not trigger discovery");
        helper.assertTrue(LandmarkDiscoveryService.isVisitTransition(null, sameCamp),
                "Leaving and re-entering a landmark did not trigger discovery");
        helper.assertTrue(!LandmarkDiscoveryService.isVisitTransition(camp, null),
                "Leaving a landmark was incorrectly treated as a discovery");

        AdvancementHolder advancement = helper.getLevel().getServer().getAdvancements()
                .get(LandmarkDiscoveryService.ADVANCEMENT);
        helper.assertTrue(advancement != null, "Landmark discovery advancement was not loaded");
        helper.assertTrue(advancement.value().criteria().containsKey(LandmarkDiscoveryService.CRITERION),
                "Landmark discovery advancement omitted its server-awarded criterion");
        helper.succeed();
    }

    private static LandmarkDefinition definition(String path, int weight, LandmarkCategory category) {
        return definition(path, "landmarks/" + path, LandmarkPlacementStyle.SURFACE,
                -32, 128, weight, category);
    }

    private static LandmarkDefinition definition(
            String path, String template, LandmarkPlacementStyle style, int minY, int maxY) {
        return definition(path, template, style, minY, maxY, 1, LandmarkCategory.SURVEY_STATION);
    }

    private static LandmarkDefinition definition(
            String path,
            String template,
            LandmarkPlacementStyle style,
            int minY,
            int maxY,
            int weight,
            LandmarkCategory category) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("test", path);
        return new LandmarkDefinition(
                id,
                ResourceLocation.fromNamespaceAndPath("test", template),
                weight,
                category,
                EnumSet.of(TerrainMode.FLAT),
                style,
                minY,
                maxY,
                LandmarkBiomeSelectors.miningBiomes(),
                List.of(ResourceKey.create(Registries.PROCESSOR_LIST,
                        ResourceLocation.fromNamespaceAndPath("test", "processor"))),
                ResourceKey.create(Registries.LOOT_TABLE,
                        ResourceLocation.fromNamespaceAndPath("test", "chests/" + path)));
    }
}
