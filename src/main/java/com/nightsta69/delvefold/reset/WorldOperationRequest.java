package com.nightsta69.delvefold.reset;

import com.nightsta69.delvefold.config.model.GameplayPreset;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.TerrainMode;

public record WorldOperationRequest(
        WorldOperationType type,
        TerrainMode targetTerrain,
        OrePreset targetOrePreset,
        GameplayPreset targetGameplayPreset,
        BackupMode backupMode,
        boolean resetOreConfiguration
) {
    public static WorldOperationRequest recreate(TerrainMode targetTerrain) {
        return new WorldOperationRequest(
                WorldOperationType.RECREATE,
                targetTerrain,
                null,
                null,
                BackupMode.KEEP_BACKUP,
                false
        );
    }

    public static WorldOperationRequest delete() {
        return new WorldOperationRequest(
                WorldOperationType.DELETE,
                null,
                null,
                null,
                BackupMode.KEEP_BACKUP,
                false
        );
    }
}
