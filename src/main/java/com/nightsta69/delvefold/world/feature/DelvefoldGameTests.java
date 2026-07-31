package com.nightsta69.delvefold.world.feature;

import com.nightsta69.delvefold.Delvefold;
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
}
