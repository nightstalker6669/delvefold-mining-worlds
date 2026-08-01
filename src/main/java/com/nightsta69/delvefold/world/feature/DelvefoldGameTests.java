package com.nightsta69.delvefold.world.feature;

import com.nightsta69.delvefold.Delvefold;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast;
import com.nightsta69.delvefold.config.model.BiomeFilter;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.OreRule;
import com.nightsta69.delvefold.config.model.OreTarget;
import com.nightsta69.delvefold.config.model.SpawnBand;
import com.nightsta69.delvefold.config.model.TerrainMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Delvefold.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DelvefoldGameTests {
    private static final String EMPTY_TEMPLATE = "bastion/mobs/empty";

    private DelvefoldGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void outputsSharingAHostAreSelectedPerVein(GameTestHelper helper) {
        OreRule rule = new OreRule(
                "mixed_gems",
                true,
                true,
                Set.of(TerrainMode.FLAT),
                List.of(
                        OreTarget.of("minecraft:diamond_ore", "minecraft:stone_ore_replaceables"),
                        OreTarget.of("minecraft:emerald_ore", "minecraft:stone_ore_replaceables")),
                new BiomeFilter(List.of(), List.of()),
                List.of(SpawnBand.uniform("main", 6, 1.0D, -32, 32, 0.0D)));
        RuntimeOreProfile profile = RuntimeOreProfile.compile(
                new OreProfileDocument(OreProfileDocument.CURRENT_SCHEMA_VERSION, 0, "test", List.of(rule)));
        RuntimeOreProfile.CompiledBand band = profile.bands(
                TerrainMode.FLAT, helper.getLevel().getBiome(helper.absolutePos(BlockPos.ZERO))).getFirst();

        Set<Block> observed = new HashSet<>();
        RandomSource random = RandomSource.create(42L);
        for (int draw = 0; draw < 64; draw++) {
            observed.add(band.ore(random).targetStates.getFirst().state.getBlock());
        }
        helper.assertTrue(observed.contains(Blocks.DIAMOND_ORE), "Diamond was never selected");
        helper.assertTrue(observed.contains(Blocks.EMERALD_ORE), "Emerald was never selected");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void defaultTargetWeightsPreserveTheLegacyRandomSequence(GameTestHelper helper) {
        RuntimeOreProfile.CompiledBand band = compileBand(helper, List.of(
                OreTarget.of("minecraft:diamond_ore", "minecraft:stone_ore_replaceables"),
                OreTarget.of("minecraft:emerald_ore", "minecraft:stone_ore_replaceables")));
        List<Block> orderedOutputs = List.of(Blocks.DIAMOND_ORE, Blocks.EMERALD_ORE);
        RandomSource expectedRandom = RandomSource.create(0x5EEDL);
        RandomSource actualRandom = RandomSource.create(0x5EEDL);

        for (int draw = 0; draw < 256; draw++) {
            Block expected = orderedOutputs.get(expectedRandom.nextInt(orderedOutputs.size()));
            Block actual = band.ore(actualRandom).targetStates.getFirst().state.getBlock();
            helper.assertTrue(actual == expected,
                    "Default target weights changed the pre-weight random sequence at draw " + draw);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void configuredTargetWeightsAreDeterministicAndEffective(GameTestHelper helper) {
        RuntimeOreProfile.CompiledBand band = compileBand(helper, List.of(
                OreTarget.of("minecraft:diamond_ore", "minecraft:stone_ore_replaceables", 9),
                OreTarget.of("minecraft:emerald_ore", "minecraft:stone_ore_replaceables", 1)));
        RandomSource first = RandomSource.create(0xC0FFEE42L);
        RandomSource second = RandomSource.create(0xC0FFEE42L);
        int diamonds = 0;

        for (int draw = 0; draw < 8192; draw++) {
            Block selected = band.ore(first).targetStates.getFirst().state.getBlock();
            Block repeated = band.ore(second).targetStates.getFirst().state.getBlock();
            helper.assertTrue(selected == repeated, "Weighted target selection was not deterministic");
            if (selected == Blocks.DIAMOND_ORE) {
                diamonds++;
            }
        }
        helper.assertTrue(diamonds > 7000 && diamonds < 7700,
                "A 9:1 target weight produced an implausible diamond count: " + diamonds);
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void tagWeightIsSharedAcrossSortedMembersAndOverlapsAreDeduplicated(GameTestHelper helper) {
        List<OreTarget> targets = List.of(
                OreTarget.ofTag("minecraft:coal_ores", "minecraft:stone_ore_replaceables", 8),
                OreTarget.of("minecraft:diamond_ore", "minecraft:stone_ore_replaceables", 2),
                OreTarget.of("minecraft:diamond_ore", "minecraft:stone_ore_replaceables", 3));
        RuntimeOreProfile.CompiledBand band = compileBand(helper, targets);
        RuntimeOreProfile.CompiledTargetGroup group = band.targetGroups().getFirst();

        helper.assertTrue(group.outputs().size() == 3,
                "Overlapping exact output was not deduplicated from the host group");
        helper.assertTrue(group.outputs().get(0).state().getBlock() == Blocks.COAL_ORE,
                "Tag members were not sorted by registry ID");
        helper.assertTrue(group.outputs().get(1).state().getBlock() == Blocks.DEEPSLATE_COAL_ORE,
                "Tag members were not sorted by registry ID");
        helper.assertTrue(group.outputs().get(2).state().getBlock() == Blocks.DIAMOND_ORE,
                "Exact output did not retain target order after the sorted tag members");
        helper.assertTrue(group.outputs().get(0).selectionWeight() == 4.0D
                        && group.outputs().get(1).selectionWeight() == 4.0D,
                "The tag-level weight was not shared equally across its members");
        helper.assertTrue(group.outputs().get(2).selectionWeight() == 2.0D,
                "An overlapping exact target changed the first candidate's selection weight");

        OreTargetResolution.Result resolution = OreTargetResolution.resolve(rule(targets));
        helper.assertTrue(resolution.effectiveOutputCount() == 3,
                "Forecast target resolution did not share runtime's deduplicated outputs");
        helper.assertTrue(resolution.shadowedOutputCount() == 1,
                "Forecast target resolution did not report the overlapping exact output");
        helper.assertTrue(resolution.targets().get(2).status()
                        == OreTargetResolution.TargetStatus.SHADOWED,
                "The fully overlapped target was not reported as shadowed");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void forecastUsesRuntimeTargetsBiomeAndDistributionMath(GameTestHelper helper) {
        OreRule rule = new OreRule(
                "forecast",
                true,
                true,
                Set.of(TerrainMode.FLAT),
                List.of(OreTarget.of("minecraft:diamond_ore", "minecraft:stone_ore_replaceables")),
                new BiomeFilter(List.of(), List.of()),
                List.of(SpawnBand.uniform("main", 8, 4.0D, 0, 3, 0.0D)));
        OreProfileDocument document = new OreProfileDocument(
                OreProfileDocument.CURRENT_SCHEMA_VERSION, 7L, "forecast", List.of(rule));

        OreProfileForecast forecast = MinecraftOreProfileForecastBuilder.build(
                document, TerrainMode.FLAT, helper.getLevel().registryAccess(), 0, 12);
        OreProfileForecast.RuleForecast ruleForecast = forecast.rules().getFirst();

        helper.assertTrue(ruleForecast.status() == OreProfileForecast.RuleStatus.EFFECTIVE,
                "A valid flat rule was not forecast as effective");
        helper.assertTrue(ruleForecast.effectiveAttempts() == 4.0D
                        && ruleForecast.effectiveWorkUnits() == 32.0D,
                "Forecast workload did not match runtime band settings");
        helper.assertTrue(ruleForecast.effectiveOutputCount() == 1,
                "Forecast did not resolve the exact output and host tag");
        for (int y = 0; y <= 3; y++) {
            OreProfileForecast.HeightSample sample = forecast.activeTerrainHeightOverlay().get(y + 64);
            helper.assertTrue(sample.y() == y && sample.expectedAttempts() == 1.0D
                            && sample.expectedWorkUnits() == 8.0D,
                    "Uniform height overlay was incorrect at Y=" + y);
        }
        helper.succeed();
    }

    private static RuntimeOreProfile.CompiledBand compileBand(
            GameTestHelper helper, List<OreTarget> targets) {
        OreRule rule = rule(targets);
        RuntimeOreProfile profile = RuntimeOreProfile.compile(
                new OreProfileDocument(OreProfileDocument.CURRENT_SCHEMA_VERSION, 0, "test", List.of(rule)));
        return profile.bands(
                TerrainMode.FLAT, helper.getLevel().getBiome(helper.absolutePos(BlockPos.ZERO))).getFirst();
    }

    private static OreRule rule(List<OreTarget> targets) {
        return new OreRule(
                "weighted_test",
                true,
                true,
                Set.of(TerrainMode.FLAT),
                targets,
                new BiomeFilter(List.of(), List.of()),
                List.of(SpawnBand.uniform("main", 6, 1.0D, -32, 32, 0.0D)));
    }
}
