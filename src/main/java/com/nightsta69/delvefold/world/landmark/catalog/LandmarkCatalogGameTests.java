package com.nightsta69.delvefold.world.landmark.catalog;

import com.nightsta69.delvefold.Delvefold;
import com.nightsta69.delvefold.config.model.TerrainMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Regression coverage for the real datapack codec, LKG policy, and reload work budget. */
@GameTestHolder(Delvefold.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LandmarkCatalogGameTests {
    private static final String EMPTY_TEMPLATE = "bastion/mobs/empty";
    private static final ResourceLocation FILE =
            ResourceLocation.fromNamespaceAndPath("test", "delvefold/landmarks/original.json");
    private static final String VALID_DEFINITION = """
            {
              "format": 1,
              "template": "test:landmarks/original",
              "weight": 4,
              "category": "survey_station",
              "terrain_modes": ["flat"],
              "placement_style": "surface",
              "min_y": -32,
              "max_y": 96,
              "biomes": {"include": ["#delvefold:mining_biomes"], "exclude": []},
              "processors": [],
              "loot_table": "test:chests/original"
            }
            """;

    private LandmarkCatalogGameTests() {}

    /**
     * Verifies that malformed and oversized reload inputs are rejected before publication while preserving the exact
     * immutable last-known-good catalog snapshot instance.
     *
     * @param helper NeoForge GameTest context used for assertions
     */
    @SuppressWarnings("ReferenceEquality")
    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void malformedListenerInputRetainsTheExactLastKnownGoodCatalog(GameTestHelper helper) {
        LandmarkCatalogService service = new LandmarkCatalogService();
        LandmarkDefinition valid =
                LandmarkCatalogReloadListener.decodeDefinition(FILE, VALID_DEFINITION.getBytes(StandardCharsets.UTF_8));
        LandmarkCatalogService.ReloadOutcome accepted =
                service.publish(Map.of(valid.id(), valid), List.of(), Instant.EPOCH);
        LandmarkCatalogSnapshot lastGood = accepted.activeSnapshot();

        String malformed = VALID_DEFINITION.replace("\"terrain_modes\": [\"flat\"]", "\"terrain_modes\": []");
        String decodeError = decodeError(malformed.getBytes(StandardCharsets.UTF_8));
        LandmarkCatalogService.ReloadOutcome rejected =
                service.publish(Map.of(), List.of(decodeError), Instant.EPOCH.plusSeconds(1));

        helper.assertTrue(accepted.applied(), "The valid definition was rejected");
        helper.assertTrue(!rejected.applied(), "Malformed listener input replaced the catalog");
        helper.assertTrue(
                rejected.activeSnapshot() == lastGood,
                "Malformed input did not retain the exact last-known-good snapshot");
        helper.assertTrue(
                rejected.diagnostics().retainingLastGood()
                        && rejected.diagnostics().errors().size() == 1,
                "Reload diagnostics omitted the retained catalog or codec error");

        byte[] oversized = new byte[LandmarkCatalogReloadListener.MAX_DEFINITION_BYTES + 1];
        helper.assertTrue(
                decodeError(oversized).contains("exceeds"),
                "Oversized definitions were not rejected before JSON decoding");
        helper.succeed();
    }

    /**
     * Verifies that reload validation caches repeated template bounds and rejects an adversarial maximum-size catalog
     * whose placement scan would exceed the bounded work budget.
     *
     * @param helper NeoForge GameTest context used for assertions
     */
    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void catalogWorkBudgetCachesBoundsAndRejectsAdversarialScans(GameTestHelper helper) {
        List<LandmarkDefinition> repeatedBounds = new ArrayList<>();
        List<LandmarkDefinition> distinctBounds = new ArrayList<>();
        for (int index = 0; index < LandmarkCatalogReloadListener.MAX_DEFINITIONS; index++) {
            repeatedBounds.add(caveDefinition("repeated_" + index, -64, 319));
            int minY = -256 + index;
            distinctBounds.add(caveDefinition("distinct_" + index, minY, minY + 383));
        }

        LandmarkCatalogWorkBudget.Analysis repeated = LandmarkCatalogWorkBudget.analyze(repeatedBounds);
        helper.assertTrue(
                repeated.distinctCaveProbes() == 1 && repeated.caveScanCellsPerCandidate() == 384,
                "Definitions sharing cave bounds did not reuse one bounded height probe");
        helper.assertTrue(
                LandmarkCatalogWorkBudget.validate(repeatedBounds).isEmpty(),
                "A catalog with one shared bounded cave scan was rejected");

        LandmarkCatalogWorkBudget.Analysis adversarial = LandmarkCatalogWorkBudget.analyze(distinctBounds);
        helper.assertTrue(
                adversarial.distinctCaveProbes() == LandmarkCatalogReloadListener.MAX_DEFINITIONS,
                "Distinct cave bounds were incorrectly coalesced");
        helper.assertTrue(
                adversarial.caveScanCellsPerCandidate() > LandmarkCatalogWorkBudget.MAX_CAVE_SCAN_CELLS_PER_CANDIDATE,
                "The adversarial catalog did not exceed the reload work budget");
        helper.assertTrue(
                LandmarkCatalogWorkBudget.validate(distinctBounds).size() == 1,
                "The adversarial catalog was not rejected with one bounded diagnostic");
        helper.succeed();
    }

    /**
     * Verifies that template dependencies exceeding horizontal span, vertical span, or serialized NBT byte limits are
     * rejected before they can invalidate the published catalog.
     *
     * @param helper NeoForge GameTest context used for assertions
     */
    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void templateDependencyBoundsRejectCatalogBreakingResources(GameTestHelper helper) {
        ResourceLocation template = ResourceLocation.fromNamespaceAndPath("test", "landmarks/oversized");
        LandmarkCatalogReloadListener.validateTemplateSize(template, templateSize(31, 20, 47));
        String horizontalError =
                templateSizeError(template, LandmarkDefinition.MAX_TEMPLATE_HORIZONTAL_SPAN + 1, 20, 8);
        helper.assertTrue(
                horizontalError.contains("exceeds Delvefold's"),
                "An oversized horizontal structure dependency was accepted");
        String verticalError = templateSizeError(template, 8, LandmarkDefinition.MAX_TEMPLATE_VERTICAL_SPAN + 1, 8);
        helper.assertTrue(
                verticalError.contains("exceeds Delvefold's"),
                "An oversized vertical structure dependency was accepted");
        helper.assertTrue(
                templateSizeError(template, 0, 1, 1).contains("positive"),
                "A structure dependency with an empty axis was accepted");
        helper.succeed();
    }

    private static String decodeError(byte[] bytes) {
        try {
            LandmarkCatalogReloadListener.decodeDefinition(FILE, bytes);
            return "";
        } catch (IllegalArgumentException exception) {
            return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        }
    }

    private static String templateSizeError(ResourceLocation id, int width, int height, int depth) {
        try {
            LandmarkCatalogReloadListener.validateTemplateSize(id, templateSize(width, height, depth));
            return "";
        } catch (IllegalArgumentException exception) {
            return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        }
    }

    private static CompoundTag templateSize(int width, int height, int depth) {
        ListTag size = new ListTag();
        size.add(IntTag.valueOf(width));
        size.add(IntTag.valueOf(height));
        size.add(IntTag.valueOf(depth));
        CompoundTag root = new CompoundTag();
        root.put("size", size);
        return root;
    }

    private static LandmarkDefinition caveDefinition(String path, int minY, int maxY) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("test", path);
        return new LandmarkDefinition(
                id,
                ResourceLocation.fromNamespaceAndPath("test", "landmarks/" + path),
                1,
                LandmarkCategory.SURVEY_STATION,
                EnumSet.of(TerrainMode.CAVERN),
                LandmarkPlacementStyle.CAVE_FLOOR,
                minY,
                maxY,
                LandmarkBiomeSelectors.miningBiomes(),
                List.<ResourceKey<StructureProcessorList>>of(),
                ResourceKey.create(
                        Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath("test", "chests/" + path)));
    }
}
