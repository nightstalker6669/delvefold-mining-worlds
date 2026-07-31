package com.nightsta69.delvefold.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.model.GameplayPreset;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.WorldSettingsDocument;
import org.junit.jupiter.api.Test;

class ConfigJsonTest {
    @Test
    void oreDocumentRoundTripsThroughCanonicalJson() {
        OreProfileDocument original = OrePresets.balanced();
        String json = ConfigJson.GSON.toJson(original);
        assertTrue(json.contains("\"schema_version\""));
        assertTrue(json.contains("\"attempts_per_chunk\""));
        OreProfileDocument decoded = ConfigJson.GSON.fromJson(json, OreProfileDocument.class);
        assertEquals(original, decoded);
    }

    @Test
    void initializationIsExplicitAndEpochChangesOnlyWithWorldLifecycle() {
        WorldSettingsDocument uninitialized = WorldSettingsDocument.uninitialized();
        assertFalse(uninitialized.initialized());
        assertEquals(0, uninitialized.generationEpoch());

        WorldSettingsDocument initialized = uninitialized.initialize(
                TerrainMode.CAVERN, OrePreset.RICH, GameplayPreset.HOSTILE);
        assertTrue(initialized.initialized());
        assertEquals(1, initialized.generationEpoch());
        assertEquals(TerrainMode.CAVERN, initialized.terrainMode());

        WorldSettingsDocument recreated = initialized.recreate(
                TerrainMode.WILD, OrePreset.VANILLA_BALANCED, GameplayPreset.SAFE, "operation-1");
        assertEquals(2, recreated.generationEpoch());
        assertEquals("operation-1", recreated.lastWorldOperationId());
        assertEquals(TerrainMode.WILD, recreated.terrainMode());

        WorldSettingsDocument deleted = recreated.markDeleted("operation-2");
        assertFalse(deleted.initialized());
        assertEquals(3, deleted.generationEpoch());
        assertEquals("operation-2", deleted.lastWorldOperationId());
    }
}
