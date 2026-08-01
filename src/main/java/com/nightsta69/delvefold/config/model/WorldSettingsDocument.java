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
        PortalSettings portal,
        String activeProfileId,
        WorldIdentitySettings identity,
        GuideVisibility guideVisibility
) {
    public static final int CURRENT_SCHEMA_VERSION = 2;

    public WorldSettingsDocument {
        lastWorldOperationId = lastWorldOperationId == null ? "" : lastWorldOperationId;
        orePreset = orePreset == null ? OrePreset.VANILLA_BALANCED : orePreset;
        gameplay = gameplay == null ? GameplaySettings.fromPreset(GameplayPreset.SAFE) : gameplay;
        portal = portal == null ? PortalSettings.defaults() : portal;
        activeProfileId = activeProfileId == null || activeProfileId.isBlank()
                ? orePreset.serializedName() : activeProfileId.trim();
        identity = identity == null ? WorldIdentitySettings.defaults() : identity;
        guideVisibility = guideVisibility == null ? GuideVisibility.PUBLIC : guideVisibility;
        if (initialized && terrainMode == null) {
            throw new IllegalArgumentException("An initialized world requires a terrain mode");
        }
    }

    /** Source- and binary-compatible constructor for schema-2 callers predating guide visibility. */
    public WorldSettingsDocument(
            int schemaVersion,
            long revision,
            long generationEpoch,
            String lastWorldOperationId,
            boolean initialized,
            TerrainMode terrainMode,
            OrePreset orePreset,
            GameplaySettings gameplay,
            PortalSettings portal,
            String activeProfileId,
            WorldIdentitySettings identity
    ) {
        this(schemaVersion, revision, generationEpoch, lastWorldOperationId, initialized, terrainMode,
                orePreset, gameplay, portal, activeProfileId, identity, GuideVisibility.PUBLIC);
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
                PortalSettings.defaults(),
                OrePreset.VANILLA_BALANCED.serializedName(),
                WorldIdentitySettings.defaults(),
                GuideVisibility.PUBLIC
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
                portal,
                preset.serializedName(),
                identity,
                guideVisibility
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
                portal,
                activeProfileId,
                identity,
                guideVisibility
        );
    }

    public WorldSettingsDocument recreate(TerrainMode mode, OrePreset preset, GameplayPreset gameplayPreset) {
        return recreate(mode, identity.terrainVariant(), preset, gameplayPreset, lastWorldOperationId);
    }

    public WorldSettingsDocument recreate(TerrainMode mode, OrePreset preset, GameplayPreset gameplayPreset, String operationId) {
        return recreate(mode, identity.terrainVariant(), preset, gameplayPreset, operationId);
    }

    public WorldSettingsDocument recreate(TerrainMode mode, TerrainVariant variant, OrePreset preset,
            GameplayPreset gameplayPreset, String operationId) {
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
                portal,
                activeProfileId,
                identity.withTerrainVariant(variant),
                guideVisibility
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
                portal,
                activeProfileId,
                identity,
                guideVisibility
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
                replacement,
                activeProfileId,
                identity,
                guideVisibility
        );
    }

    public WorldSettingsDocument withActiveProfile(String replacement) {
        return new WorldSettingsDocument(CURRENT_SCHEMA_VERSION, revision, generationEpoch, lastWorldOperationId,
                initialized, terrainMode, orePreset, gameplay, portal, replacement, identity, guideVisibility);
    }

    public WorldSettingsDocument withIdentity(WorldIdentitySettings replacement) {
        return new WorldSettingsDocument(CURRENT_SCHEMA_VERSION, revision, generationEpoch, lastWorldOperationId,
                initialized, terrainMode, orePreset, gameplay, portal, activeProfileId, replacement, guideVisibility);
    }

    public WorldSettingsDocument withGuideVisibility(GuideVisibility replacement) {
        return new WorldSettingsDocument(CURRENT_SCHEMA_VERSION, revision, generationEpoch, lastWorldOperationId,
                initialized, terrainMode, orePreset, gameplay, portal, activeProfileId, identity, replacement);
    }
}
