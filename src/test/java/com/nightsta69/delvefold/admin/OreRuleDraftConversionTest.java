package com.nightsta69.delvefold.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.network.model.AdminSnapshot;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OreRuleDraftConversionTest {
    @Test
    void advancedGuiFieldsBecomeCanonicalOreRuleFields() {
        AdminSnapshot.OreRuleDraft draft = new AdminSnapshot.OreRuleDraft(
                "example.tin",
                true,
                false,
                "example:tin_ore",
                List.of(new AdminSnapshot.OreVariantDraft(
                        "example:tin_ore", "", "minecraft:stone_ore_replaceables",
                        Map.of("lit", "true"), 37)),
                List.of(TerrainMode.CAVERN),
                List.of("#delvefold:mining_biomes", "example:deep_caves"),
                List.of("minecraft:plains"),
                List.of(AdminSnapshot.OreBandDraft.defaultBand()));

        var rule = DefaultDelvefoldAdminService.fromDraft(draft);

        assertEquals(Map.of("lit", "true"), rule.targets().getFirst().state());
        assertEquals(37, rule.targets().getFirst().weight());
        assertEquals(List.of("#delvefold:mining_biomes", "example:deep_caves"), rule.biomes().include());
        assertEquals(List.of("minecraft:plains"), rule.biomes().exclude());
        assertEquals(java.util.Set.of(TerrainMode.CAVERN), rule.terrainModes());

        AdminSnapshot.OreRuleDraft encodedForGui = DefaultDelvefoldAdminService.toDraft(rule);
        assertEquals(37, encodedForGui.variants().getFirst().weight());
    }
}
