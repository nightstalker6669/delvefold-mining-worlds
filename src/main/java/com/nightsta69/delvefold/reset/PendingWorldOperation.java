package com.nightsta69.delvefold.reset;

import com.nightsta69.delvefold.config.model.GameplayPreset;
import com.nightsta69.delvefold.config.model.GeologyTheme;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.TerrainVariant;
import org.jspecify.annotations.Nullable;

/**
 * Restart journal for an accepted delete or recreation operation.
 *
 * <p>The server writes this immutable value before evacuating players and performs the destructive filesystem phase
 * only during the next ordered server startup. Nullable target fields are meaningful only where the operation type or
 * optional reset choice does not require them.
 *
 * @param schemaVersion on-disk journal schema, currently {@value #CURRENT_SCHEMA_VERSION}
 * @param operationId UUID text binding retries, history, and configuration commit state
 * @param type requested lifecycle operation
 * @param sourceTerrain terrain active when the request was accepted, or {@code null} when unavailable
 * @param targetTerrain recreation terrain, or {@code null} for deletion
 * @param targetVariant recreation terrain variant, or {@code null} when not applicable
 * @param targetGeologyTheme recreation geology theme, or {@code null} when not applicable
 * @param targetOrePreset optional built-in ore preset to apply after recreation
 * @param targetGameplayPreset optional gameplay preset to apply after recreation
 * @param backupMode whether finalized staging is retained or permanently removed
 * @param resetOreConfiguration whether recreation resets the active ore document
 * @param createdAtEpochMillis acceptance time in epoch milliseconds
 * @param requestedBy bounded actor label recorded for history and audit attribution
 */
public record PendingWorldOperation(
        int schemaVersion,
        String operationId,
        WorldOperationType type,
        @Nullable TerrainMode sourceTerrain,
        @Nullable TerrainMode targetTerrain,
        @Nullable TerrainVariant targetVariant,
        @Nullable GeologyTheme targetGeologyTheme,
        @Nullable OrePreset targetOrePreset,
        @Nullable GameplayPreset targetGameplayPreset,
        BackupMode backupMode,
        boolean resetOreConfiguration,
        long createdAtEpochMillis,
        String requestedBy) {
    /** Current schema accepted by startup recovery and backup-manifest validation. */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    /**
     * Normalizes persisted text/defaults and enforces the required recreation target.
     *
     * @param schemaVersion on-disk journal schema
     * @param operationId lifecycle operation UUID text
     * @param type requested operation
     * @param sourceTerrain previously active terrain, when known
     * @param targetTerrain requested recreation terrain
     * @param targetVariant requested recreation variant
     * @param targetGeologyTheme requested recreation geology theme
     * @param targetOrePreset optional ore preset reset
     * @param targetGameplayPreset optional gameplay preset reset
     * @param backupMode finalized staging policy
     * @param resetOreConfiguration whether to reset the ore document
     * @param createdAtEpochMillis acceptance time in epoch milliseconds
     * @param requestedBy actor label
     * @throws IllegalArgumentException if a recreation has no target terrain
     */
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

    /**
     * Creates a source-compatible journal for callers predating geology themes, selecting the Classic theme.
     *
     * @param schemaVersion on-disk journal schema
     * @param operationId lifecycle operation UUID text
     * @param type requested operation
     * @param sourceTerrain previously active terrain, when known
     * @param targetTerrain requested recreation terrain
     * @param targetVariant requested recreation variant
     * @param targetOrePreset optional ore preset reset
     * @param targetGameplayPreset optional gameplay preset reset
     * @param backupMode finalized staging policy
     * @param resetOreConfiguration whether to reset the ore document
     * @param createdAtEpochMillis acceptance time in epoch milliseconds
     * @param requestedBy actor label
     * @throws IllegalArgumentException if a recreation has no target terrain
     */
    public PendingWorldOperation(
            int schemaVersion,
            String operationId,
            WorldOperationType type,
            @Nullable TerrainMode sourceTerrain,
            @Nullable TerrainMode targetTerrain,
            @Nullable TerrainVariant targetVariant,
            @Nullable OrePreset targetOrePreset,
            @Nullable GameplayPreset targetGameplayPreset,
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
