package com.nightsta69.delvefold.config.model;

public record WorldSettingsDocument(
        int schemaVersion,
        long revision,
        long generationEpoch,
        long generationSalt,
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
    private static final long GENERATION_SALT_DOMAIN = 0x6A09E667F3BCC909L;

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

    /** Source- and binary-compatible constructor for schema-2 callers predating generation salts. */
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
            WorldIdentitySettings identity,
            GuideVisibility guideVisibility
    ) {
        this(schemaVersion, revision, generationEpoch, 0L, lastWorldOperationId, initialized, terrainMode,
                orePreset, gameplay, portal, activeProfileId, identity, guideVisibility);
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
        this(schemaVersion, revision, generationEpoch, 0L, lastWorldOperationId, initialized, terrainMode,
                orePreset, gameplay, portal, activeProfileId, identity, GuideVisibility.PUBLIC);
    }

    public static WorldSettingsDocument uninitialized() {
        return new WorldSettingsDocument(
                CURRENT_SCHEMA_VERSION,
                0,
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
        return initialize(mode, preset, gameplayPreset, identity);
    }

    public WorldSettingsDocument initialize(TerrainMode mode, OrePreset preset, GameplayPreset gameplayPreset,
            WorldIdentitySettings replacementIdentity) {
        if (initialized) {
            throw new IllegalStateException("Delvefold is already initialized");
        }
        WorldIdentitySettings selectedIdentity = replacementIdentity == null ? identity : replacementIdentity;
        long nextEpoch = nextGenerationEpoch();
        return new WorldSettingsDocument(
                CURRENT_SCHEMA_VERSION,
                revision + 1,
                nextEpoch,
                generationSalt(selectedIdentity.renewal().seedMode(), nextEpoch),
                lastWorldOperationId,
                true,
                mode,
                preset,
                GameplaySettings.fromPreset(gameplayPreset),
                portal,
                preset.serializedName(),
                selectedIdentity,
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
                nextGenerationEpoch(),
                0L,
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
        return recreate(mode, variant, identity.geologyTheme(), preset, gameplayPreset, operationId);
    }

    public WorldSettingsDocument recreate(TerrainMode mode, TerrainVariant variant, GeologyTheme geologyTheme,
            OrePreset preset, GameplayPreset gameplayPreset, String operationId) {
        if (!initialized) {
            throw new IllegalStateException("Delvefold must be initialized before it can be recreated");
        }
        long nextEpoch = nextGenerationEpoch();
        return new WorldSettingsDocument(
                CURRENT_SCHEMA_VERSION,
                revision + 1,
                nextEpoch,
                generationSalt(identity.renewal().seedMode(), nextEpoch),
                operationId,
                true,
                mode,
                preset == null ? orePreset : preset,
                gameplayPreset == null ? gameplay : GameplaySettings.fromPreset(gameplayPreset),
                portal,
                activeProfileId,
                identity.withTerrainAndGeology(variant, geologyTheme),
                guideVisibility
        );
    }

    public WorldSettingsDocument withGameplay(GameplaySettings replacement) {
        return new WorldSettingsDocument(
                CURRENT_SCHEMA_VERSION,
                revision,
                generationEpoch,
                generationSalt,
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
                generationSalt,
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
        return new WorldSettingsDocument(CURRENT_SCHEMA_VERSION, revision, generationEpoch, generationSalt,
                lastWorldOperationId,
                initialized, terrainMode, orePreset, gameplay, portal, replacement, identity, guideVisibility);
    }

    public WorldSettingsDocument withIdentity(WorldIdentitySettings replacement) {
        return new WorldSettingsDocument(CURRENT_SCHEMA_VERSION, revision, generationEpoch, generationSalt,
                lastWorldOperationId,
                initialized, terrainMode, orePreset, gameplay, portal, activeProfileId, replacement, guideVisibility);
    }

    public WorldSettingsDocument withGuideVisibility(GuideVisibility replacement) {
        return new WorldSettingsDocument(CURRENT_SCHEMA_VERSION, revision, generationEpoch, generationSalt,
                lastWorldOperationId,
                initialized, terrainMode, orePreset, gameplay, portal, activeProfileId, identity, replacement);
    }

    private long nextGenerationEpoch() {
        if (generationEpoch == Long.MAX_VALUE) {
            throw new IllegalStateException("Generation epoch cannot be incremented beyond " + Long.MAX_VALUE);
        }
        return generationEpoch + 1L;
    }

    private static long generationSalt(RenewalSeedMode mode, long epoch) {
        if (mode != RenewalSeedMode.ROTATE_ON_RECREATE) {
            return 0L;
        }
        long value = epoch ^ GENERATION_SALT_DOMAIN;
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        value ^= value >>> 31;
        value &= Long.MAX_VALUE;
        return value == 0L ? 1L : value;
    }
}
