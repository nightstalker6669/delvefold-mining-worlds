package com.nightsta69.delvefold.config.model;

import org.jspecify.annotations.Nullable;

/**
 * Immutable schema-2 mining-world settings and lifecycle snapshot.
 *
 * <p>{@code revision} protects accepted configuration mutations, while {@code generationEpoch} and the persisted
 * {@code generationSalt} identify recreation generations. Stable renewal mode stores salt zero for compatibility;
 * rotating mode derives a nonzero salt only when initialization or recreation commits. Reloading or restarting cannot
 * silently move ore, province, geology, or landmark layouts in an existing generation.
 *
 * @param schemaVersion serialized settings schema, currently {@value #CURRENT_SCHEMA_VERSION}
 * @param revision nonnegative optimistic-concurrency revision for accepted settings mutations
 * @param generationEpoch monotonically increasing generation counter changed by initialization, deletion, or recreation
 * @param generationSalt persisted deterministic layout salt; zero selects the compatibility layout
 * @param lastWorldOperationId opaque accepted lifecycle operation ID used for idempotent recovery
 * @param initialized whether a mining-world generation is configured and available
 * @param terrainMode recreation-locked terrain family, required when initialized and absent otherwise
 * @param orePreset bundled preset last selected during initialization or recreation
 * @param gameplay live natural-spawning policy
 * @param portal live portal access and routing policy
 * @param activeProfileId active local or namespaced ore-profile ID
 * @param identity player-facing identity plus recreation, landmark, and renewal policy
 * @param guideVisibility live read-only guide permission policy; omitted schema-2 values default to public
 * @param backupRetention optional live retention limits; omitted schema-2 values default to disabled
 */
public record WorldSettingsDocument(
        int schemaVersion,
        long revision,
        long generationEpoch,
        long generationSalt,
        String lastWorldOperationId,
        boolean initialized,
        @Nullable TerrainMode terrainMode,
        OrePreset orePreset,
        GameplaySettings gameplay,
        PortalSettings portal,
        String activeProfileId,
        WorldIdentitySettings identity,
        GuideVisibility guideVisibility,
        BackupRetentionSettings backupRetention) {
    /** Stable configuration schema supported throughout the Delvefold 1.x series. */
    public static final int CURRENT_SCHEMA_VERSION = 2;

    private static final long GENERATION_SALT_DOMAIN = 0x6A09E667F3BCC909L;

    /**
     * Creates a lifecycle snapshot and applies additive schema-2 compatibility defaults.
     *
     * <p>An initialized snapshot without a terrain mode is rejected immediately. Other malformed ranges and lifecycle
     * combinations remain available to the settings validator so diagnostics can retain exact JSON paths.
     *
     * @param schemaVersion serialized settings schema
     * @param revision optimistic-concurrency revision
     * @param generationEpoch generation counter
     * @param generationSalt persisted deterministic layout salt
     * @param lastWorldOperationId accepted lifecycle operation ID
     * @param initialized whether a mining-world generation is configured
     * @param terrainMode selected terrain, or {@code null} only while uninitialized
     * @param orePreset bundled ore preset
     * @param gameplay natural-spawning policy
     * @param portal portal access and routing policy
     * @param activeProfileId active ore-profile ID
     * @param identity mining-world identity and generation policy
     * @param guideVisibility guide permission policy
     * @param backupRetention backup-retention policy
     * @throws IllegalArgumentException if {@code initialized} is true and {@code terrainMode} is {@code null}
     */
    public WorldSettingsDocument {
        lastWorldOperationId = lastWorldOperationId == null ? "" : lastWorldOperationId;
        orePreset = orePreset == null ? OrePreset.VANILLA_BALANCED : orePreset;
        gameplay = gameplay == null ? GameplaySettings.fromPreset(GameplayPreset.SAFE) : gameplay;
        portal = portal == null ? PortalSettings.defaults() : portal;
        activeProfileId = activeProfileId == null || activeProfileId.isBlank()
                ? orePreset.serializedName()
                : activeProfileId.trim();
        identity = identity == null ? WorldIdentitySettings.defaults() : identity;
        guideVisibility = guideVisibility == null ? GuideVisibility.PUBLIC : guideVisibility;
        backupRetention = backupRetention == null ? BackupRetentionSettings.defaults() : backupRetention;
        if (initialized && terrainMode == null) {
            throw new IllegalArgumentException("An initialized world requires a terrain mode");
        }
    }

    /**
     * Creates source-compatible settings for schema-2 callers predating backup retention.
     *
     * @param schemaVersion serialized settings schema
     * @param revision optimistic-concurrency revision
     * @param generationEpoch generation counter
     * @param generationSalt persisted deterministic layout salt
     * @param lastWorldOperationId accepted lifecycle operation ID
     * @param initialized whether a mining-world generation is configured
     * @param terrainMode selected terrain, or {@code null} only while uninitialized
     * @param orePreset bundled ore preset
     * @param gameplay natural-spawning policy
     * @param portal portal access and routing policy
     * @param activeProfileId active ore-profile ID
     * @param identity mining-world identity and generation policy
     * @param guideVisibility guide permission policy
     */
    public WorldSettingsDocument(
            int schemaVersion,
            long revision,
            long generationEpoch,
            long generationSalt,
            String lastWorldOperationId,
            boolean initialized,
            @Nullable TerrainMode terrainMode,
            OrePreset orePreset,
            GameplaySettings gameplay,
            PortalSettings portal,
            String activeProfileId,
            WorldIdentitySettings identity,
            GuideVisibility guideVisibility) {
        this(
                schemaVersion,
                revision,
                generationEpoch,
                generationSalt,
                lastWorldOperationId,
                initialized,
                terrainMode,
                orePreset,
                gameplay,
                portal,
                activeProfileId,
                identity,
                guideVisibility,
                BackupRetentionSettings.defaults());
    }

    /**
     * Creates source- and binary-compatible settings for schema-2 callers predating persisted generation salts.
     *
     * @param schemaVersion serialized settings schema
     * @param revision optimistic-concurrency revision
     * @param generationEpoch generation counter
     * @param lastWorldOperationId accepted lifecycle operation ID
     * @param initialized whether a mining-world generation is configured
     * @param terrainMode selected terrain, or {@code null} only while uninitialized
     * @param orePreset bundled ore preset
     * @param gameplay natural-spawning policy
     * @param portal portal access and routing policy
     * @param activeProfileId active ore-profile ID
     * @param identity mining-world identity and generation policy
     * @param guideVisibility guide permission policy
     */
    public WorldSettingsDocument(
            int schemaVersion,
            long revision,
            long generationEpoch,
            String lastWorldOperationId,
            boolean initialized,
            @Nullable TerrainMode terrainMode,
            OrePreset orePreset,
            GameplaySettings gameplay,
            PortalSettings portal,
            String activeProfileId,
            WorldIdentitySettings identity,
            GuideVisibility guideVisibility) {
        this(
                schemaVersion,
                revision,
                generationEpoch,
                0L,
                lastWorldOperationId,
                initialized,
                terrainMode,
                orePreset,
                gameplay,
                portal,
                activeProfileId,
                identity,
                guideVisibility,
                BackupRetentionSettings.defaults());
    }

    /**
     * Creates source- and binary-compatible settings for schema-2 callers predating guide visibility.
     *
     * @param schemaVersion serialized settings schema
     * @param revision optimistic-concurrency revision
     * @param generationEpoch generation counter
     * @param lastWorldOperationId accepted lifecycle operation ID
     * @param initialized whether a mining-world generation is configured
     * @param terrainMode selected terrain, or {@code null} only while uninitialized
     * @param orePreset bundled ore preset
     * @param gameplay natural-spawning policy
     * @param portal portal access and routing policy
     * @param activeProfileId active ore-profile ID
     * @param identity mining-world identity and generation policy
     */
    public WorldSettingsDocument(
            int schemaVersion,
            long revision,
            long generationEpoch,
            String lastWorldOperationId,
            boolean initialized,
            @Nullable TerrainMode terrainMode,
            OrePreset orePreset,
            GameplaySettings gameplay,
            PortalSettings portal,
            String activeProfileId,
            WorldIdentitySettings identity) {
        this(
                schemaVersion,
                revision,
                generationEpoch,
                0L,
                lastWorldOperationId,
                initialized,
                terrainMode,
                orePreset,
                gameplay,
                portal,
                activeProfileId,
                identity,
                GuideVisibility.PUBLIC,
                BackupRetentionSettings.defaults());
    }

    /**
     * Returns the canonical first-run settings snapshot.
     *
     * @return schema-2 revision-zero snapshot with no terrain, stable generation state, and all compatibility defaults
     */
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
                GuideVisibility.PUBLIC,
                BackupRetentionSettings.defaults());
    }

    /**
     * Creates the first initialized lifecycle snapshot using the current identity.
     *
     * <p>This pure transition increments the settings revision and generation epoch, derives and persists the selected
     * generation salt, and activates the bundled preset ID. Filesystem and level creation occur in the lifecycle
     * service after this transition is accepted.
     *
     * @param mode recreation-locked terrain family
     * @param preset bundled starting ore preset
     * @param gameplayPreset live natural-spawning preset
     * @return initialized immutable snapshot at the next revision and generation epoch
     * @throws IllegalStateException if this snapshot is already initialized
     */
    public WorldSettingsDocument initialize(TerrainMode mode, OrePreset preset, GameplayPreset gameplayPreset) {
        return initialize(mode, preset, gameplayPreset, identity);
    }

    /**
     * Creates the first initialized lifecycle snapshot with an optional replacement identity.
     *
     * <p>The replacement identity supplies recreation-locked variant, theme, and seed policy for the new generation. A
     * {@code null} replacement preserves this snapshot's identity.
     *
     * @param mode recreation-locked terrain family
     * @param preset bundled starting ore preset
     * @param gameplayPreset live natural-spawning preset
     * @param replacementIdentity complete identity to install, or {@code null} to preserve the current identity
     * @return initialized immutable snapshot at the next revision and generation epoch
     * @throws IllegalStateException if this snapshot is already initialized
     */
    public WorldSettingsDocument initialize(
            TerrainMode mode,
            OrePreset preset,
            GameplayPreset gameplayPreset,
            @Nullable WorldIdentitySettings replacementIdentity) {
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
                guideVisibility,
                backupRetention);
    }

    /**
     * Produces the uninitialized state recorded after confirmed deletion, retaining the current operation ID.
     *
     * @return uninitialized snapshot with revision and generation epoch incremented and generation salt cleared
     * @throws IllegalStateException if the generation epoch is already {@link Long#MAX_VALUE}
     */
    public WorldSettingsDocument markDeleted() {
        return markDeleted(lastWorldOperationId);
    }

    /**
     * Produces the uninitialized state recorded after a named confirmed deletion.
     *
     * <p>The transition clears terrain and generation salt but preserves gameplay, portal, profile, identity, guide,
     * and retention choices for a later initialization. It does not itself remove dimension files.
     *
     * @param operationId opaque accepted lifecycle operation ID to persist for idempotent recovery
     * @return uninitialized snapshot with revision and generation epoch incremented
     * @throws IllegalStateException if the generation epoch is already {@link Long#MAX_VALUE}
     */
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
                guideVisibility,
                backupRetention);
    }

    /**
     * Produces the next recreation snapshot using the current terrain variant, geology theme, and operation ID.
     *
     * @param mode replacement terrain family
     * @param preset replacement bundled ore preset selection
     * @param gameplayPreset replacement natural-spawning preset
     * @return initialized snapshot at the next revision and generation epoch
     * @throws IllegalStateException if this snapshot is uninitialized or its generation epoch cannot be incremented
     */
    public WorldSettingsDocument recreate(
            TerrainMode mode, @Nullable OrePreset preset, @Nullable GameplayPreset gameplayPreset) {
        return recreate(mode, identity.terrainVariant(), preset, gameplayPreset, lastWorldOperationId);
    }

    /**
     * Produces the next recreation snapshot using the current terrain variant and geology theme.
     *
     * @param mode replacement terrain family
     * @param preset replacement bundled ore preset selection
     * @param gameplayPreset replacement natural-spawning preset
     * @param operationId opaque accepted lifecycle operation ID to persist
     * @return initialized snapshot at the next revision and generation epoch
     * @throws IllegalStateException if this snapshot is uninitialized or its generation epoch cannot be incremented
     */
    public WorldSettingsDocument recreate(
            TerrainMode mode, @Nullable OrePreset preset, @Nullable GameplayPreset gameplayPreset, String operationId) {
        return recreate(mode, identity.terrainVariant(), preset, gameplayPreset, operationId);
    }

    /**
     * Produces the next recreation snapshot using a replacement terrain variant and the current geology theme.
     *
     * @param mode replacement terrain family
     * @param variant replacement terrain scale
     * @param preset replacement bundled ore preset selection
     * @param gameplayPreset replacement natural-spawning preset
     * @param operationId opaque accepted lifecycle operation ID to persist
     * @return initialized snapshot at the next revision and generation epoch
     * @throws IllegalStateException if this snapshot is uninitialized or its generation epoch cannot be incremented
     */
    public WorldSettingsDocument recreate(
            TerrainMode mode,
            TerrainVariant variant,
            @Nullable OrePreset preset,
            @Nullable GameplayPreset gameplayPreset,
            String operationId) {
        return recreate(mode, variant, identity.geologyTheme(), preset, gameplayPreset, operationId);
    }

    /**
     * Produces the complete next recreation snapshot.
     *
     * <p>This pure transition increments revision and generation epoch and persists the salt derived from the selected
     * renewal seed mode. It preserves the active named profile, portal, guide, retention, renewal, display-name, and
     * landmark settings. Backup creation, evacuation, and restart-safe filesystem replacement remain lifecycle-service
     * responsibilities.
     *
     * @param mode replacement terrain family
     * @param variant replacement terrain scale
     * @param geologyTheme replacement bounded geology theme
     * @param preset replacement bundled ore preset selection
     * @param gameplayPreset replacement natural-spawning preset
     * @param operationId opaque accepted lifecycle operation ID to persist
     * @return initialized snapshot at the next revision and generation epoch
     * @throws IllegalStateException if this snapshot is uninitialized or its generation epoch cannot be incremented
     */
    public WorldSettingsDocument recreate(
            TerrainMode mode,
            TerrainVariant variant,
            GeologyTheme geologyTheme,
            @Nullable OrePreset preset,
            @Nullable GameplayPreset gameplayPreset,
            String operationId) {
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
                guideVisibility,
                backupRetention);
    }

    /**
     * Copies this snapshot with a replacement live natural-spawning policy.
     *
     * <p>The value copier intentionally preserves {@link #revision()}; the configuration service assigns and audits
     * accepted mutation revisions.
     *
     * @param replacement replacement gameplay settings
     * @return immutable settings copy retaining all lifecycle and generation fields
     */
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
                guideVisibility,
                backupRetention);
    }

    /**
     * Copies this snapshot with a replacement live portal policy.
     *
     * @param replacement replacement portal access and routing settings
     * @return immutable settings copy with the same revision and generation identity
     */
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
                guideVisibility,
                backupRetention);
    }

    /**
     * Copies this snapshot with a replacement active ore-profile ID.
     *
     * <p>After service validation and publication, the profile applies only to chunks generated afterward. Existing
     * chunk contents are not retrogened. This copier does not increment the settings revision.
     *
     * @param replacement local profile ID or namespaced datapack/script profile ID
     * @return immutable settings copy with the replacement active profile
     */
    public WorldSettingsDocument withActiveProfile(String replacement) {
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
                portal,
                replacement,
                identity,
                guideVisibility,
                backupRetention);
    }

    /**
     * Copies this snapshot with a complete replacement identity.
     *
     * <p>This value copier does not enforce live-versus-recreation locks or increment the revision. The configuration
     * service must reject an initialized live update that changes terrain variant, geology theme, generation epoch,
     * salt, initialization state, or operation ID.
     *
     * @param replacement replacement world identity
     * @return immutable settings copy with unchanged lifecycle fields
     */
    public WorldSettingsDocument withIdentity(WorldIdentitySettings replacement) {
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
                portal,
                activeProfileId,
                replacement,
                guideVisibility,
                backupRetention);
    }

    /**
     * Copies this snapshot with a replacement live guide-access policy.
     *
     * @param replacement replacement guide visibility
     * @return immutable settings copy with the same revision and generation identity
     */
    public WorldSettingsDocument withGuideVisibility(GuideVisibility replacement) {
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
                portal,
                activeProfileId,
                identity,
                replacement,
                backupRetention);
    }

    /**
     * Copies this snapshot with replacement live backup-retention limits.
     *
     * <p>Retention remains inactive unless explicitly enabled. Enforcement additionally protects pinned backups, the
     * newest two backups, and backups referenced by pending operations.
     *
     * @param replacement replacement retention settings
     * @return immutable settings copy with the same revision and generation identity
     */
    public WorldSettingsDocument withBackupRetention(BackupRetentionSettings replacement) {
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
                portal,
                activeProfileId,
                identity,
                guideVisibility,
                replacement);
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
