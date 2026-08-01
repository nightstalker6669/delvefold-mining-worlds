package com.nightsta69.delvefold.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.ConfigSnapshot;
import com.nightsta69.delvefold.config.OrePresets;
import com.nightsta69.delvefold.config.model.BiomeFilter;
import com.nightsta69.delvefold.config.model.GameplayPreset;
import com.nightsta69.delvefold.config.model.GeologyTheme;
import com.nightsta69.delvefold.config.model.HeightDistribution;
import com.nightsta69.delvefold.config.model.LandmarkPreset;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.OreRule;
import com.nightsta69.delvefold.config.model.OreTarget;
import com.nightsta69.delvefold.config.model.PortalSettings;
import com.nightsta69.delvefold.config.model.ProvinceSettings;
import com.nightsta69.delvefold.config.model.RenewalSettings;
import com.nightsta69.delvefold.config.model.SpawnBand;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.TerrainVariant;
import com.nightsta69.delvefold.config.model.WorldIdentitySettings;
import com.nightsta69.delvefold.config.model.WorldSettingsDocument;
import com.nightsta69.delvefold.config.validation.ValidationReport;
import com.nightsta69.delvefold.guide.GuideSnapshot.OutputKind;
import com.nightsta69.delvefold.guide.GuideSnapshot.PortalStatus;
import com.nightsta69.delvefold.guide.GuideSnapshot.RelativeFrequency;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GuideSnapshotBuilderTest {
    @Test
    void publishesOnlyBoundedPlayerFacingWorldAndOreInformation() {
        long now = 1_000_000L;
        WorldIdentitySettings identity = new WorldIdentitySettings(
                "Public Mine",
                TerrainVariant.EXPANSIVE,
                LandmarkPreset.BALANCED,
                true,
                true,
                true,
                GeologyTheme.CRYSTAL,
                new RenewalSettings(true, 30, 30, now + 90_001L));
        WorldSettingsDocument settings = WorldSettingsDocument.uninitialized()
                .withIdentity(identity)
                .initialize(TerrainMode.CAVERN, OrePreset.VANILLA_BALANCED, GameplayPreset.SAFE)
                .withPortal(new PortalSettings(true, true, 5, 1.0D));
        GuideSnapshot guide = GuideSnapshotBuilder.build(
                snapshot(OrePresets.balanced(), settings),
                now,
                false,
                (kind, sourceId) ->
                        Optional.of(kind == OutputKind.BLOCK_TAG ? "example:representative_ore" : sourceId));

        assertEquals("Public Mine", guide.worldName());
        assertEquals("cavern", guide.terrain());
        assertEquals("expansive", guide.terrainVariant());
        assertEquals("crystal", guide.geologyTheme());
        assertEquals(PortalStatus.AVAILABLE, guide.portalStatus());
        assertTrue(guide.renewal().scheduled());
        assertEquals(91L, guide.renewal().remainingSeconds());
        assertFalse(guide.ores().isEmpty());
        assertTrue(guide.ores().stream().allMatch(entry -> entry.applicability().appliesToActiveTerrain()));
        assertTrue(guide.ores().stream()
                .allMatch(entry -> entry.applicability().biomeIncludes().contains("#delvefold:mining_biomes")));
        assertTrue(guide.ores().stream().allMatch(entry -> !entry.heightBands().isEmpty()));
        assertTrue(guide.estimatedNetworkBytes() <= GuideLimits.MAX_ESTIMATED_NETWORK_BYTES);
    }

    @Test
    void representsTagOutputsAndPeakHeightWithoutLeakingReplacementDetails() {
        OreRule tagged = new OreRule(
                "tin",
                true,
                false,
                EnumSet.of(TerrainMode.WILD),
                List.of(OreTarget.ofTag("c:ores/tin", "minecraft:stone_ore_replaceables")),
                BiomeFilter.ALL_MINING_BIOMES,
                List.of(SpawnBand.triangle("main", 6, 4.0D, -32, 80, 12, 0.0D)));
        WorldSettingsDocument settings = WorldSettingsDocument.uninitialized()
                .initialize(TerrainMode.WILD, OrePreset.EMPTY, GameplayPreset.SAFE)
                .withActiveProfile("pack:metals");
        GuideSnapshot guide = GuideSnapshotBuilder.build(
                snapshot(new OreProfileDocument(2, 7, "pack:metals", List.of(tagged)), settings),
                0L,
                false,
                (kind, id) -> Optional.of("example:tin_ore"));

        var entry = guide.ores().getFirst();
        assertEquals(OutputKind.BLOCK_TAG, entry.outputs().getFirst().kind());
        assertEquals("c:ores/tin", entry.outputs().getFirst().sourceId());
        assertEquals("example:tin_ore", entry.outputs().getFirst().iconBlockId());
        assertEquals(12, entry.heightBands().getFirst().bestMinY());
        assertEquals(12, entry.heightBands().getFirst().bestMaxY());
        assertEquals(RelativeFrequency.ABUNDANT, entry.relativeFrequency());
    }

    @Test
    void representsProvincePlacementAndCountsItsBoundedWorkInRelativeFrequency() {
        SpawnBand province = SpawnBand.province(
                "regional",
                HeightDistribution.TRIANGLE,
                -48,
                96,
                24,
                null,
                null,
                0.0D,
                new ProvinceSettings(512, 160, 40, 0.04D, 768));
        OreRule rule = new OreRule(
                "regional_tin",
                true,
                false,
                EnumSet.of(TerrainMode.FLAT),
                List.of(OreTarget.of("example:tin_ore", "minecraft:stone_ore_replaceables")),
                BiomeFilter.ALL_MINING_BIOMES,
                List.of(province));
        WorldSettingsDocument settings = WorldSettingsDocument.uninitialized()
                .initialize(TerrainMode.FLAT, OrePreset.EMPTY, GameplayPreset.SAFE);

        GuideSnapshot guide = GuideSnapshotBuilder.build(
                snapshot(new OreProfileDocument(2, 1, "province", List.of(rule)), settings),
                0L,
                false,
                GuideIconResolver.NONE);

        var entry = guide.ores().getFirst();
        assertEquals(RelativeFrequency.ABUNDANT, entry.relativeFrequency());
        assertEquals("province_triangle", entry.heightBands().getFirst().distribution());
        assertEquals(24, entry.heightBands().getFirst().bestMinY());
        assertEquals(1, entry.heightBands().getFirst().veinSize());
    }

    @Test
    void provinceGuideUsesAPlacementMarkerRatherThanTheUnusedCanonicalVeinSize() {
        SpawnBand province = new SpawnBand(
                "regional",
                0,
                0.0D,
                HeightDistribution.UNIFORM,
                -32,
                64,
                null,
                null,
                null,
                0.0D,
                com.nightsta69.delvefold.config.model.OreBandPlacement.PROVINCE,
                ProvinceSettings.defaults());
        OreRule rule = new OreRule(
                "regional_tin",
                true,
                false,
                EnumSet.of(TerrainMode.FLAT),
                List.of(OreTarget.of("example:tin_ore", "minecraft:stone_ore_replaceables")),
                BiomeFilter.ALL_MINING_BIOMES,
                List.of(province));
        WorldSettingsDocument settings = WorldSettingsDocument.uninitialized()
                .initialize(TerrainMode.FLAT, OrePreset.EMPTY, GameplayPreset.SAFE);

        GuideSnapshot guide = GuideSnapshotBuilder.build(
                snapshot(new OreProfileDocument(2, 1, "province", List.of(rule)), settings),
                0L,
                false,
                GuideIconResolver.NONE);

        assertEquals(1, guide.ores().getFirst().heightBands().getFirst().veinSize());
    }

    @Test
    void truncatesLargeProfilesToCountAndNetworkBudgets() {
        List<OreRule> rules = new ArrayList<>();
        for (int index = 0; index < 512; index++) {
            String suffix = Integer.toString(index);
            String id = "ore_" + suffix + "_" + "x".repeat(110);
            List<OreTarget> targets = new ArrayList<>();
            for (int target = 0; target < 16; target++) {
                targets.add(OreTarget.of(
                        "example:ore_" + suffix + "_" + target + "_" + "y".repeat(90),
                        "minecraft:stone_ore_replaceables"));
            }
            List<SpawnBand> bands = new ArrayList<>();
            for (int band = 0; band < 16; band++) {
                bands.add(SpawnBand.uniform("band_" + band + "_" + "z".repeat(100), 8, 4.0D, -64, 128, 0.0D));
            }
            rules.add(new OreRule(
                    id, true, false, EnumSet.allOf(TerrainMode.class), targets, BiomeFilter.ALL_MINING_BIOMES, bands));
        }
        WorldSettingsDocument settings = WorldSettingsDocument.uninitialized()
                .initialize(TerrainMode.FLAT, OrePreset.EMPTY, GameplayPreset.SAFE);
        GuideSnapshot guide = GuideSnapshotBuilder.build(
                snapshot(new OreProfileDocument(2, 1, "oversized", rules), settings),
                0L,
                false,
                GuideIconResolver.NONE);

        assertTrue(guide.truncated());
        assertTrue(guide.ores().size() <= GuideLimits.MAX_ORE_ENTRIES);
        assertTrue(guide.estimatedNetworkBytes() <= GuideLimits.MAX_ESTIMATED_NETWORK_BYTES);
        assertTrue(guide.ores().stream()
                .allMatch(entry -> entry.outputs().size() <= GuideLimits.MAX_OUTPUTS_PER_ENTRY
                        && entry.heightBands().size() <= GuideLimits.MAX_HEIGHT_BANDS_PER_ENTRY));
    }

    @Test
    void portalAndRenewalStatusesDoNotExposeInternalOperationDetails() {
        WorldSettingsDocument uninitialized = WorldSettingsDocument.uninitialized();
        GuideSnapshot first = GuideSnapshotBuilder.build(
                snapshot(OrePresets.empty(), uninitialized), 0L, true, GuideIconResolver.NONE);
        assertEquals(PortalStatus.UNINITIALIZED, first.portalStatus());

        WorldSettingsDocument initialized =
                uninitialized.initialize(TerrainMode.FLAT, OrePreset.EMPTY, GameplayPreset.SAFE);
        GuideSnapshot blocked =
                GuideSnapshotBuilder.build(snapshot(OrePresets.empty(), initialized), 0L, true, GuideIconResolver.NONE);
        assertEquals(PortalStatus.ENTRY_BLOCKED, blocked.portalStatus());
        assertFalse(blocked.renewal().scheduled());
    }

    private static ConfigSnapshot snapshot(OreProfileDocument ores, WorldSettingsDocument settings) {
        return new ConfigSnapshot(
                ores,
                settings,
                new ValidationReport(List.of()),
                Instant.EPOCH,
                "private-disk-hash-must-never-be-copied");
    }
}
