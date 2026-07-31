package com.nightsta69.delvefold.network;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.nightsta69.delvefold.config.model.GameplayPreset;
import com.nightsta69.delvefold.config.model.GameplaySettings;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.PortalSettings;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.network.model.AdminSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminSnapshotTest {
    @Test
    void missingCapabilitiesFailClosed() {
        AdminSnapshot snapshot = new AdminSnapshot(0, 0, true, false, TerrainMode.FLAT,
                OrePreset.VANILLA_BALANCED, GameplaySettings.fromPreset(GameplayPreset.SAFE),
                PortalSettings.defaults(), null, "vanilla_balanced", List.of(),
                "portal", "world", false, List.of(), 0, 0, List.of());

        assertFalse(snapshot.capabilities().canConfigure());
        assertFalse(snapshot.capabilities().canManageWorld());
        assertFalse(snapshot.capabilities().canRestoreBackups());
    }
}
