package com.nightsta69.delvefold.reset;

import com.nightsta69.delvefold.config.model.GameplayPreset;
import com.nightsta69.delvefold.config.model.GeologyTheme;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.TerrainVariant;
import org.jspecify.annotations.Nullable;

/**
 * Immutable administrator request to delete or recreate the active mining world.
 *
 * @param type requested lifecycle operation
 * @param targetTerrain recreation terrain, or {@code null} for deletion or an incomplete request
 * @param targetVariant recreation terrain variant, or {@code null} when not applicable
 * @param targetGeologyTheme recreation geology theme, or {@code null} to use the compatibility default
 * @param targetOrePreset optional built-in ore preset to apply after recreation
 * @param targetGameplayPreset optional gameplay preset to apply after recreation
 * @param backupMode whether finalized staging is retained or permanently removed
 * @param resetOreConfiguration whether recreation resets the active ore document
 */
public record WorldOperationRequest(
        WorldOperationType type,
        @Nullable TerrainMode targetTerrain,
        @Nullable TerrainVariant targetVariant,
        @Nullable GeologyTheme targetGeologyTheme,
        @Nullable OrePreset targetOrePreset,
        @Nullable GameplayPreset targetGameplayPreset,
        BackupMode backupMode,
        boolean resetOreConfiguration) {
    /**
     * Creates a source-compatible request for callers written before geology themes.
     *
     * @param type requested lifecycle operation
     * @param targetTerrain recreation terrain
     * @param targetVariant recreation terrain variant
     * @param targetOrePreset optional ore preset reset
     * @param targetGameplayPreset optional gameplay preset reset
     * @param backupMode finalized-staging policy
     * @param resetOreConfiguration whether to reset the active ore document
     */
    public WorldOperationRequest(
            WorldOperationType type,
            @Nullable TerrainMode targetTerrain,
            @Nullable TerrainVariant targetVariant,
            @Nullable OrePreset targetOrePreset,
            @Nullable GameplayPreset targetGameplayPreset,
            BackupMode backupMode,
            boolean resetOreConfiguration) {
        this(
                type,
                targetTerrain,
                targetVariant,
                null,
                targetOrePreset,
                targetGameplayPreset,
                backupMode,
                resetOreConfiguration);
    }

    /**
     * Creates a recreation request using the Classic terrain variant.
     *
     * @param targetTerrain requested terrain, or {@code null} for a request that validation will reject
     * @return immutable recreation request retaining the current profile and gameplay settings
     */
    public static WorldOperationRequest recreate(@Nullable TerrainMode targetTerrain) {
        return recreate(targetTerrain, TerrainVariant.CLASSIC);
    }

    /**
     * Creates a recreation request using the selected terrain variant and compatibility geology theme.
     *
     * @param targetTerrain requested terrain, or {@code null} for a request that validation will reject
     * @param targetVariant requested terrain variant
     * @return immutable recreation request retaining the current profile and gameplay settings
     */
    public static WorldOperationRequest recreate(@Nullable TerrainMode targetTerrain, TerrainVariant targetVariant) {
        return new WorldOperationRequest(
                WorldOperationType.RECREATE,
                targetTerrain,
                targetVariant,
                null,
                null,
                null,
                BackupMode.KEEP_BACKUP,
                false);
    }

    /**
     * Creates a recreation request with explicit terrain, variant, and optional geology theme.
     *
     * @param targetTerrain requested terrain, or {@code null} for a request that validation will reject
     * @param targetVariant requested terrain variant
     * @param targetGeologyTheme requested geology theme, or {@code null} for the compatibility default
     * @return immutable recreation request retaining the current profile and gameplay settings
     */
    public static WorldOperationRequest recreate(
            @Nullable TerrainMode targetTerrain,
            TerrainVariant targetVariant,
            @Nullable GeologyTheme targetGeologyTheme) {
        return new WorldOperationRequest(
                WorldOperationType.RECREATE,
                targetTerrain,
                targetVariant,
                targetGeologyTheme,
                null,
                null,
                BackupMode.KEEP_BACKUP,
                false);
    }

    /**
     * Creates a delete request that retains a recoverable backup by default.
     *
     * @return immutable delete request with no recreation targets
     */
    public static WorldOperationRequest delete() {
        return new WorldOperationRequest(
                WorldOperationType.DELETE, null, null, null, null, null, BackupMode.KEEP_BACKUP, false);
    }
}
