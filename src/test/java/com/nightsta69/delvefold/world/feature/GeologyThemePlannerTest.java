package com.nightsta69.delvefold.world.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.model.GeologyTheme;
import com.nightsta69.delvefold.world.feature.GeologyThemeConfiguration.Phase;
import com.nightsta69.delvefold.world.feature.GeologyThemePlanner.Material;
import com.nightsta69.delvefold.world.feature.GeologyThemePlanner.Role;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class GeologyThemePlannerTest {
    private static final int MINIMUM_Y = -60;
    private static final int MAXIMUM_Y = 315;

    private static final Map<GeologyTheme, Set<Material>> STRATA_PALETTES = Map.of(
            GeologyTheme.VOLCANIC, Set.of(Material.TUFF, Material.BASALT, Material.BLACKSTONE),
            GeologyTheme.DRIPSTONE, Set.of(Material.DRIPSTONE_BLOCK, Material.CALCITE, Material.TUFF),
            GeologyTheme.LUSH, Set.of(Material.CLAY, Material.MUD, Material.ROOTED_DIRT, Material.MOSS_BLOCK),
            GeologyTheme.CRYSTAL, Set.of(Material.CALCITE, Material.SMOOTH_BASALT, Material.AMETHYST_BLOCK));

    @Test
    void classicIsABitForBitNoOp() {
        assertTrue(plan(GeologyTheme.CLASSIC, Phase.STRATA, 0L).placements().isEmpty());
        assertTrue(
                plan(GeologyTheme.CLASSIC, Phase.DECORATIONS, 0L).placements().isEmpty());
    }

    @Test
    void absentPhaseUsesTheCompatibilityStrataDefault() {
        assertEquals(plan(GeologyTheme.VOLCANIC, Phase.STRATA, 0L), plan(GeologyTheme.VOLCANIC, null, 0L));
    }

    @Test
    void plansAreDeterministicAndGenerationSaltRotatesThem() {
        var first = plan(GeologyTheme.CRYSTAL, Phase.STRATA, 17L);
        var repeated = plan(GeologyTheme.CRYSTAL, Phase.STRATA, 17L);
        var rotated = plan(GeologyTheme.CRYSTAL, Phase.STRATA, 18L);

        assertEquals(first, repeated);
        assertFalse(first.equals(rotated));
    }

    @Test
    void everyThemeStaysInsideTheCurrentChunkHeightAndPalette() {
        int chunkX = -7;
        int chunkZ = 11;
        int minimumX = chunkX * 16;
        int minimumZ = chunkZ * 16;
        for (GeologyTheme theme : STRATA_PALETTES.keySet()) {
            var plan = GeologyThemePlanner.plan(theme, Phase.STRATA, 0x5EEDL, chunkX, chunkZ, 0L, MINIMUM_Y, MAXIMUM_Y);
            assertFalse(plan.placements().isEmpty());
            assertTrue(plan.placements().size() <= GeologyThemePlanner.MAX_STRATA_PLACEMENTS);
            assertTrue(plan.placements().stream()
                    .allMatch(placement -> placement.role() == Role.STRATA
                            && placement.position().x() >= minimumX
                            && placement.position().x() < minimumX + 16
                            && placement.position().z() >= minimumZ
                            && placement.position().z() < minimumZ + 16
                            && placement.position().y() >= MINIMUM_Y
                            && placement.position().y() <= MAXIMUM_Y
                            && STRATA_PALETTES.get(theme).contains(placement.material())));
            assertEquals(
                    plan.placements().size(),
                    plan.placements().stream()
                            .map(GeologyThemePlanner.Placement::position)
                            .distinct()
                            .count());
        }
    }

    @Test
    void decorationAndFluidBudgetsAreHardLimits() {
        for (GeologyTheme theme : STRATA_PALETTES.keySet()) {
            var plan = plan(theme, Phase.DECORATIONS, 0L);
            long fluids = plan.placements().stream()
                    .filter(value -> value.role() == Role.FLUID)
                    .count();
            assertEquals(
                    GeologyThemePlanner.MAX_DECORATION_PLACEMENTS,
                    plan.placements().size());
            assertTrue(fluids <= GeologyThemePlanner.MAX_FLUID_PLACEMENTS);
            assertTrue(plan.placements().stream().allMatch(value -> value.role() != Role.STRATA));
            assertTrue(plan.placements().stream()
                    .filter(value -> value.role() == Role.FLUID)
                    .allMatch(value ->
                            value.material() == (theme == GeologyTheme.VOLCANIC ? Material.LAVA : Material.WATER)));
        }
    }

    @Test
    void planRejectsPlacementsAboveTheHardBudget() {
        var excessive = IntStream.rangeClosed(0, GeologyThemePlanner.MAX_STRATA_PLACEMENTS)
                .mapToObj(index -> new GeologyThemePlanner.Placement(
                        new GeologyThemePlanner.Position(index, 0, 0), Material.TUFF, Role.STRATA))
                .toList();

        assertThrows(IllegalArgumentException.class, () -> new GeologyThemePlanner.Plan(excessive));
    }

    private static GeologyThemePlanner.Plan plan(GeologyTheme theme, @Nullable Phase phase, long generationSalt) {
        return GeologyThemePlanner.plan(theme, phase, 0x123456789ABCDEFL, -7, 11, generationSalt, MINIMUM_Y, MAXIMUM_Y);
    }
}
