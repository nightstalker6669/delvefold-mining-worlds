package com.nightsta69.delvefold.reset;

import com.nightsta69.delvefold.config.model.GameplayPreset;
import com.nightsta69.delvefold.config.model.GeologyTheme;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.TerrainVariant;

public record PendingWorldOperation(
        int schemaVersion,
        String operationId,
        WorldOperationType type,
        TerrainMode sourceTerrain,
        TerrainMode targetTerrain,
        TerrainVariant targetVariant,
        GeologyTheme targetGeologyTheme,
        OrePreset targetOrePreset,
        GameplayPreset targetGameplayPreset,
        BackupMode backupMode,
        boolean resetOreConfiguration,
        long createdAtEpochMillis,
        String requestedBy) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public PendingWorldOperation {
        operationId = operationId == null ? "" : operationId;
        backupMode = backupMode == null ? BackupMode.KEEP_BACKUP : backupMode;
        requestedBy = requestedBy == null ? "unknown" : requestedBy;
        if (type == WorldOperationType.RECREATE && targetTerrain == null) {
            throw new IllegalArgumentException("A recreate operation requires a target terrain");
        }
        if (type == WorldOperationType.RECREATE && targetVariant == null) {
            targetVariant = TerrainVariant.CLASSIC;
        }
        if (type == WorldOperationType.RECREATE && targetGeologyTheme == null) {
            targetGeologyTheme = GeologyTheme.CLASSIC;
        }
    }

    /** Source-compatible constructor for pending-operation callers predating geology themes. */
    public PendingWorldOperation(
            int schemaVersion,
            String operationId,
            WorldOperationType type,
            TerrainMode sourceTerrain,
            TerrainMode targetTerrain,
            TerrainVariant targetVariant,
            OrePreset targetOrePreset,
            GameplayPreset targetGameplayPreset,
            BackupMode backupMode,
            boolean resetOreConfiguration,
            long createdAtEpochMillis,
            String requestedBy) {
        this(
                schemaVersion,
                operationId,
                type,
                sourceTerrain,
                targetTerrain,
                targetVariant,
                GeologyTheme.CLASSIC,
                targetOrePreset,
                targetGameplayPreset,
                backupMode,
                resetOreConfiguration,
                createdAtEpochMillis,
                requestedBy);
    }
}
