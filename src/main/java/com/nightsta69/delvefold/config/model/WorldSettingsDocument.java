package com.nightsta69.delvefold.config.model;

public record WorldSettingsDocument(
        int schemaVersion,
        long revision,
        long generationEpoch,
        String lastWorldOperationId,
        boolean initialized,
        TerrainMode terrainMode,
        OrePreset orePreset,
        GameplaySettings gameplay,
        PortalSettings portal
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public WorldSettingsDocument {
        lastWorldOperationId = lastWorldOperationId == null ? "" : lastWorldOperationId;
        orePreset = orePreset == null ? OrePreset.VANILLA_BALANCED : orePreset;
        gameplay = gameplay == null ? GameplaySettings.fromPreset(GameplayPreset.SAFE) : gameplay;
        portal = portal == null ? PortalSettings.defaults() : portal;
        if (initialized && terrainMode == null) {
            throw new IllegalArgumentException("An initialized world requires a terrain mode");
        }
    }

    public static WorldSettingsDocument uninitialized() {
        return new WorldSettingsDocument(
                CURRENT_SCHEMA_VERSION,
                0,
                0,
                "",
                false,
                null,
                OrePreset.VANILLA_BALANCED,
                GameplaySettings.fromPreset(GameplayPreset.SAFE),
                PortalSettings.defaults()
        );
    }

    public WorldSettingsDocument initialize(TerrainMode mode, OrePreset preset, GameplayPreset gameplayPreset) {
        if (initialized) {
            throw new IllegalStateException("Delvefold is already initialized");
        }
        return new WorldSettingsDocument(
                CURRENT_SCHEMA_VERSION,
                revision + 1,
                generationEpoch + 1,
                lastWorldOperationId,
                true,
                mode,
                preset,
                GameplaySettings.fromPreset(gameplayPreset),
                portal
        );
    }

    public WorldSettingsDocument markDeleted() {
        return markDeleted(lastWorldOperationId);
    }

    public WorldSettingsDocument markDeleted(String operationId) {
        return new WorldSettingsDocument(
                CURRENT_SCHEMA_VERSION,
                revision + 1,
                generationEpoch + 1,
                operationId,
                false,
                null,
                orePreset,
                gameplay,
                portal
        );
    }

    public WorldSettingsDocument recreate(TerrainMode mode, OrePreset preset, GameplayPreset gameplayPreset) {
        return recreate(mode, preset, gameplayPreset, lastWorldOperationId);
    }

    public WorldSettingsDocument recreate(TerrainMode mode, OrePreset preset, GameplayPreset gameplayPreset, String operationId) {
        if (!initialized) {
            throw new IllegalStateException("Delvefold must be initialized before it can be recreated");
        }
        return new WorldSettingsDocument(
                CURRENT_SCHEMA_VERSION,
                revision + 1,
                generationEpoch + 1,
                operationId,
                true,
                mode,
                preset == null ? orePreset : preset,
                gameplayPreset == null ? gameplay : GameplaySettings.fromPreset(gameplayPreset),
                portal
        );
    }

    public WorldSettingsDocument withGameplay(GameplaySettings replacement) {
        return new WorldSettingsDocument(
                CURRENT_SCHEMA_VERSION,
                revision,
                generationEpoch,
                lastWorldOperationId,
                initialized,
                terrainMode,
                orePreset,
                replacement,
                portal
        );
    }

    public WorldSettingsDocument withPortal(PortalSettings replacement) {
        return new WorldSettingsDocument(
                CURRENT_SCHEMA_VERSION,
                revision,
                generationEpoch,
                lastWorldOperationId,
                initialized,
                terrainMode,
                orePreset,
                gameplay,
                replacement
        );
    }
}
