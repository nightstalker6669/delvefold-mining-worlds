package com.nightsta69.delvefold.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.admin.AdminLocalizedMessage;
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
    void unavailableFallbackCarriesTranslationKeysInsteadOfEnglishProse() {
        AdminSnapshot snapshot = AdminSnapshot.unavailable();

        assertEquals(
                "message.delvefold.admin.snapshot.backend_portal_disabled",
                AdminLocalizedMessage.decode(snapshot.portalStatus())
                        .orElseThrow()
                        .translationKey());
        assertEquals(
                "message.delvefold.admin.snapshot.backend_unavailable",
                AdminLocalizedMessage.decode(snapshot.worldStatus())
                        .orElseThrow()
                        .translationKey());
        assertEquals(
                "message.delvefold.admin.snapshot.backend_diagnostic",
                AdminLocalizedMessage.decode(snapshot.diagnostics().getFirst())
                        .orElseThrow()
                        .translationKey());
    }

    @Test
    void missingCapabilitiesFailClosed() {
        AdminSnapshot snapshot = new AdminSnapshot(
                0,
                0,
                true,
                false,
                TerrainMode.FLAT,
                OrePreset.VANILLA_BALANCED,
                GameplaySettings.fromPreset(GameplayPreset.SAFE),
                PortalSettings.defaults(),
                com.nightsta69.delvefold.config.model.WorldIdentitySettings.defaults(),
                null,
                "vanilla_balanced",
                List.of(),
                List.of(),
                "portal",
                "world",
                false,
                List.of(),
                0,
                0,
                List.of());

        assertFalse(snapshot.capabilities().canConfigure());
        assertFalse(snapshot.capabilities().canManageWorld());
        assertFalse(snapshot.capabilities().canRestoreBackups());
    }

    @Test
    void oreVariantWeightsDefaultAndRejectOutOfRangeValues() {
        AdminSnapshot.OreVariantDraft legacy = new AdminSnapshot.OreVariantDraft(
                "example:tin_ore", "", "minecraft:stone_ore_replaceables", java.util.Map.of());
        assertEquals(1, legacy.weight());
        assertEquals(
                1000,
                new AdminSnapshot.OreVariantDraft(
                                "example:tin_ore", "", "minecraft:stone_ore_replaceables", java.util.Map.of(), 1000)
                        .weight());

        assertThrows(
                IllegalArgumentException.class,
                () -> new AdminSnapshot.OreVariantDraft(
                        "example:tin_ore", "", "minecraft:stone_ore_replaceables", java.util.Map.of(), 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AdminSnapshot.OreVariantDraft(
                        "example:tin_ore", "", "minecraft:stone_ore_replaceables", java.util.Map.of(), 1001));
    }

    @Test
    void backupIntegrityStateIsExplicitAndLegacyCompatible() {
        var legacy = new AdminSnapshot.BackupDraft("legacy", 0L, "recreate", "flat", 10L, false, false, true);
        var verified = new AdminSnapshot.BackupDraft(
                "verified", 0L, "recreate", "flat", 10L, false, true, true, true, true, false);
        var unverified = new AdminSnapshot.BackupDraft(
                "unverified", 0L, "recreate", "flat", 10L, false, false, true, true, false, false);
        var invalid = new AdminSnapshot.BackupDraft(
                "invalid", 0L, "recreate", "flat", 10L, false, false, false, true, false, false);

        assertEquals("legacy", legacy.integrityState());
        assertEquals("verified", verified.integrityState());
        assertEquals("unverified", unverified.integrityState());
        assertEquals("invalid", invalid.integrityState());
    }

    @Test
    void pendingOperationPreservesBothRecoveryPathsForDualJournals() {
        assertEquals(AdminSnapshot.PendingOperation.NONE, AdminSnapshot.PendingOperation.resolve(false, false));
        assertEquals(
                AdminSnapshot.PendingOperation.WORLD_OPERATION, AdminSnapshot.PendingOperation.resolve(true, false));
        assertEquals(AdminSnapshot.PendingOperation.RESTORE, AdminSnapshot.PendingOperation.resolve(false, true));
        assertEquals(AdminSnapshot.PendingOperation.BOTH, AdminSnapshot.PendingOperation.resolve(true, true));

        AdminSnapshot both = snapshot(AdminSnapshot.PendingOperation.BOTH);
        assertTrue(both.resetPending());
        assertTrue(both.worldOperationPending());
        assertTrue(both.restorePending());
    }

    @Test
    void legacyBooleanConstructorMapsPendingStateToWorldOperation() {
        AdminSnapshot snapshot = new AdminSnapshot(
                0,
                0,
                true,
                false,
                TerrainMode.FLAT,
                OrePreset.VANILLA_BALANCED,
                GameplaySettings.fromPreset(GameplayPreset.SAFE),
                PortalSettings.defaults(),
                com.nightsta69.delvefold.config.model.WorldIdentitySettings.defaults(),
                AdminSnapshot.AdminCapabilities.none(),
                "vanilla_balanced",
                List.of(),
                List.of(),
                "portal",
                "world",
                true,
                List.of(),
                0,
                0,
                List.of());

        assertEquals(AdminSnapshot.PendingOperation.WORLD_OPERATION, snapshot.pendingOperation());
        assertTrue(snapshot.worldOperationPending());
        assertFalse(snapshot.restorePending());
    }

    private static AdminSnapshot snapshot(AdminSnapshot.PendingOperation pendingOperation) {
        return new AdminSnapshot(
                0,
                0,
                true,
                false,
                TerrainMode.FLAT,
                OrePreset.VANILLA_BALANCED,
                GameplaySettings.fromPreset(GameplayPreset.SAFE),
                PortalSettings.defaults(),
                com.nightsta69.delvefold.config.model.WorldIdentitySettings.defaults(),
                AdminSnapshot.AdminCapabilities.none(),
                "vanilla_balanced",
                List.of(),
                List.of(),
                "portal",
                "world",
                pendingOperation,
                List.of(),
                0,
                0,
                List.of());
    }
}
