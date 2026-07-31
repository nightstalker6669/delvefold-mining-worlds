package com.nightsta69.delvefold.reset;

import com.nightsta69.delvefold.config.model.GameplayPreset;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.TerrainVariant;

public record WorldOperationRequest(
        WorldOperationType type,
        TerrainMode targetTerrain,
        TerrainVariant targetVariant,
        OrePreset targetOrePreset,
        GameplayPreset targetGameplayPreset,
        BackupMode backupMode,
        boolean resetOreConfiguration
) {
    public static WorldOperationRequest recreate(TerrainMode targetTerrain) {
        return recreate(targetTerrain, TerrainVariant.CLASSIC);
    }

    public static WorldOperationRequest recreate(TerrainMode targetTerrain, TerrainVariant targetVariant) {
        return new WorldOperationRequest(
                WorldOperationType.RECREATE,
                targetTerrain,
                targetVariant,
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
                null,
                BackupMode.KEEP_BACKUP,
                false
        );
    }
}
