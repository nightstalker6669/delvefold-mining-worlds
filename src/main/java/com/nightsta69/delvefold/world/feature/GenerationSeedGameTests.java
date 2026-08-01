package com.nightsta69.delvefold.world.feature;

import com.nightsta69.delvefold.Delvefold;
import com.nightsta69.delvefold.world.DelvefoldWorldgen;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Delvefold.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GenerationSeedGameTests {
    private static final String EMPTY_TEMPLATE = "bastion/mobs/empty";

    private GenerationSeedGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void zeroSaltLandmarksPreserveLegacyDrawOrderAndPosition(GameTestHelper helper) {
        long randomSeed = randomSeedForRarityResult(true);
        RandomSource expected = RandomSource.create(randomSeed);
        RandomSource actual = RandomSource.create(randomSeed);
        BlockPos base = new BlockPos(-32, 0, 48);

        float rarityDraw = expected.nextFloat();
        BlockPos expectedCandidate = rarityDraw < 1.0F / 12.0F
                ? base.offset(expected.nextInt(16), 0, expected.nextInt(16))
                : null;
        BlockPos expectedContentOrigin = expectedCandidate.offset(
                expected.nextInt(16), 0, expected.nextInt(16));
        BlockPos actualCandidate = GenerationSaltedLandmarkPlacement.candidateOrigin(
                actual, 99L, base, 0L, 12).orElse(null);
        RandomSource selected = MiningLandmarkFeature.randomForContent(
                actual, 99L, new ChunkPos(base), 0L);
        BlockPos actualContentOrigin = actualCandidate.offset(
                selected.nextInt(16), 0, selected.nextInt(16));

        helper.assertTrue(selected == actual, "Zero salt did not preserve the placed-feature random instance");
        helper.assertTrue(expectedCandidate.equals(actualCandidate), "Legacy in-square landmark position changed");
        helper.assertTrue(expectedContentOrigin.equals(actualContentOrigin),
                "Modifier-to-feature landmark draw order changed");
        helper.assertTrue(expected.nextLong() == selected.nextLong(),
                "Legacy landmark compatibility consumed a different random sequence");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void rejectedLegacyLandmarkConsumesOnlyTheRarityDraw(GameTestHelper helper) {
        long randomSeed = randomSeedForRarityResult(false);
        RandomSource expected = RandomSource.create(randomSeed);
        RandomSource actual = RandomSource.create(randomSeed);

        expected.nextFloat();
        helper.assertTrue(GenerationSaltedLandmarkPlacement.candidateOrigin(
                        actual, 99L, BlockPos.ZERO, 0L, 12).isEmpty(),
                "A rejected legacy landmark unexpectedly produced an origin");
        helper.assertTrue(expected.nextLong() == actual.nextLong(),
                "A rejected landmark consumed more than the legacy rarity draw");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void rotatedLandmarkPlacementAndContentAreRestartStableAndDomainSeparated(GameTestHelper helper) {
        long worldSeed = 0x5EEDL;
        List<BlockPos> firstCandidates = new ArrayList<>();
        List<BlockPos> repeatedCandidates = new ArrayList<>();
        List<BlockPos> rotatedCandidates = new ArrayList<>();
        for (int chunkX = -128; chunkX < 128; chunkX++) {
            BlockPos origin = new BlockPos(chunkX * 16, 0, 37 * 16);
            RandomSource passed = RandomSource.create(chunkX);
            RandomSource untouched = RandomSource.create(chunkX);
            GenerationSaltedLandmarkPlacement.candidateOrigin(
                    passed, worldSeed, origin, 71L, 12).ifPresent(firstCandidates::add);
            GenerationSaltedLandmarkPlacement.candidateOrigin(
                    RandomSource.create(chunkX + 1L), worldSeed, origin, 71L, 12)
                    .ifPresent(repeatedCandidates::add);
            GenerationSaltedLandmarkPlacement.candidateOrigin(
                    RandomSource.create(chunkX), worldSeed, origin, 72L, 12)
                    .ifPresent(rotatedCandidates::add);
            helper.assertTrue(passed.nextLong() == untouched.nextLong(),
                    "Rotated placement consumed the passed feature random in chunk " + chunkX);
        }
        helper.assertTrue(firstCandidates.equals(repeatedCandidates),
                "Persisted landmark placement salt was not deterministic");
        helper.assertTrue(!firstCandidates.equals(rotatedCandidates),
                "Changing generation salt did not rotate landmark candidates");

        ChunkPos chunk = new ChunkPos(-11, 37);
        RandomSource first = MiningLandmarkFeature.randomForContent(
                RandomSource.create(1L), 0x5EEDL, chunk, 71L);
        RandomSource repeated = MiningLandmarkFeature.randomForContent(
                RandomSource.create(2L), 0x5EEDL, chunk, 71L);
        RandomSource rotated = MiningLandmarkFeature.randomForContent(
                RandomSource.create(1L), 0x5EEDL, chunk, 72L);

        for (int draw = 0; draw < 16; draw++) {
            helper.assertTrue(first.nextLong() == repeated.nextLong(),
                    "Persisted landmark salt was not deterministic at draw " + draw);
        }
        helper.assertTrue(
                MiningLandmarkFeature.randomForContent(
                        RandomSource.create(1L), 0x5EEDL, chunk, 71L).nextLong() != rotated.nextLong(),
                "Changing the landmark generation salt did not rotate its content stream");
        helper.assertTrue(
                GenerationSeedMixer.landmarkPlacementSeed(worldSeed, chunk.toLong(), 71L)
                        != GenerationSeedMixer.landmarkContentSeed(worldSeed, chunk.toLong(), 71L),
                "Landmark placement and content shared the same deterministic seed domain");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void saltedLandmarkModifierIsRegisteredAndLoadedFromData(GameTestHelper helper) {
        PlacedFeature placed = helper.getLevel().registryAccess()
                .registryOrThrow(Registries.PLACED_FEATURE)
                .getOrThrow(DelvefoldWorldgen.MINING_LANDMARK_PLACED);
        helper.assertTrue(placed.placement().size() == 1,
                "Mining landmarks must use exactly one combined placement modifier");
        helper.assertTrue(placed.placement().getFirst() instanceof GenerationSaltedLandmarkPlacement,
                "The generation-salted landmark placement codec was not loaded");
        GenerationSaltedLandmarkPlacement modifier =
                (GenerationSaltedLandmarkPlacement) placed.placement().getFirst();
        helper.assertTrue(modifier.chance() == 12,
                "The bundled landmark placement chance was not decoded from JSON");
        helper.succeed();
    }

    private static long randomSeedForRarityResult(boolean accepted) {
        for (long seed = 0L; seed < 10_000L; seed++) {
            if ((RandomSource.create(seed).nextFloat() < 1.0F / 12.0F) == accepted) {
                return seed;
            }
        }
        throw new AssertionError("Could not find a deterministic rarity-test seed");
    }
}
