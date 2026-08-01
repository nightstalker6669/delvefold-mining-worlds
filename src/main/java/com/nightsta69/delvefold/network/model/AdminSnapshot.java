package com.nightsta69.delvefold.network.model;

import com.nightsta69.delvefold.admin.AdminLocalizedMessage;
import com.nightsta69.delvefold.config.model.GameplayPreset;
import com.nightsta69.delvefold.config.model.GameplaySettings;
import com.nightsta69.delvefold.config.model.HeightDistribution;
import com.nightsta69.delvefold.config.model.OreBandPlacement;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.OreTarget;
import com.nightsta69.delvefold.config.model.PortalSettings;
import com.nightsta69.delvefold.config.model.ProvinceSettings;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.WorldIdentitySettings;
import com.nightsta69.delvefold.network.ProtocolLimits;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Immutable, bounded server snapshot used to render the administration GUI. The server remains the source of truth;
 * clients never mutate this object in place, and every write echoes the applicable ore or settings revision for an
 * atomic stale-write check. List containers are defensively copied into unmodifiable, protocol-bounded views.
 *
 * @param oreRevision authoritative ore-configuration revision
 * @param settingsRevision authoritative settings revision
 * @param backendReady whether the backend is installed and compatible for mutations
 * @param initialized whether initial mining-world configuration has been committed
 * @param terrainMode current terrain mode
 * @param orePreset current ore preset
 * @param gameplay current gameplay settings
 * @param portal current portal settings
 * @param identity current world identity settings
 * @param capabilities permissions and backend capabilities computed for the receiving player
 * @param activeProfileId identifier of the active ore profile
 * @param profiles bounded, immutable view of available profile summaries
 * @param backups bounded, immutable view of backup summaries
 * @param portalStatus localized display status for portal configuration
 * @param worldStatus localized display status for mining-world state
 * @param pendingOperation server-authoritative pending restart-time operation state
 * @param diagnostics bounded, immutable diagnostic messages visible to the receiving player
 * @param oreRuleTotal total number of authoritative ore rules across all pages
 * @param orePage zero-based page represented by {@code oreRules}
 * @param oreRules bounded, immutable ore-rule page
 */
public record AdminSnapshot(
        long oreRevision,
        long settingsRevision,
        boolean backendReady,
        boolean initialized,
        TerrainMode terrainMode,
        OrePreset orePreset,
        GameplaySettings gameplay,
        PortalSettings portal,
        WorldIdentitySettings identity,
        AdminCapabilities capabilities,
        String activeProfileId,
        List<ProfileDraft> profiles,
        List<BackupDraft> backups,
        String portalStatus,
        String worldStatus,
        PendingOperation pendingOperation,
        List<String> diagnostics,
        int oreRuleTotal,
        int orePage,
        List<OreRuleDraft> oreRules) {

    /**
     * Normalizes defaults and protocol bounds and takes immutable copies of all list containers.
     *
     * @param oreRevision authoritative ore-configuration revision; negative values become zero
     * @param settingsRevision authoritative settings revision; negative values become zero
     * @param backendReady whether the backend is available for mutation
     * @param initialized whether initial world configuration has been committed
     * @param terrainMode current mode, or {@code null} for the safe flat default
     * @param orePreset current preset, or {@code null} for the balanced default
     * @param gameplay current gameplay settings, or {@code null} for the safe preset
     * @param portal current portal settings, or {@code null} for defaults
     * @param identity current identity settings, or {@code null} for defaults
     * @param capabilities player capabilities, or {@code null} for no capabilities
     * @param activeProfileId active profile identifier, normalized and bounded
     * @param profiles profile summaries to copy and bound; {@code null} becomes empty
     * @param backups backup summaries to copy and bound; {@code null} becomes empty
     * @param portalStatus localized portal status, normalized and bounded
     * @param worldStatus localized world status, normalized and bounded
     * @param pendingOperation pending operation, or {@code null} for none
     * @param diagnostics diagnostic strings to sanitize, copy, and bound; {@code null} becomes empty
     * @param oreRuleTotal total authoritative rule count, clamped to protocol bounds
     * @param orePage zero-based page, clamped to protocol bounds
     * @param oreRules page rules to copy and bound; {@code null} becomes empty
     * @throws NullPointerException if a copied profile, backup, or ore-rule list contains a null element
     */
    public AdminSnapshot(
            long oreRevision,
            long settingsRevision,
            boolean backendReady,
            boolean initialized,
            @Nullable TerrainMode terrainMode,
            @Nullable OrePreset orePreset,
            @Nullable GameplaySettings gameplay,
            @Nullable PortalSettings portal,
            @Nullable WorldIdentitySettings identity,
            @Nullable AdminCapabilities capabilities,
            @Nullable String activeProfileId,
            @Nullable List<ProfileDraft> profiles,
            @Nullable List<BackupDraft> backups,
            @Nullable String portalStatus,
            @Nullable String worldStatus,
            @Nullable PendingOperation pendingOperation,
            @Nullable List<String> diagnostics,
            int oreRuleTotal,
            int orePage,
            @Nullable List<OreRuleDraft> oreRules) {
        oreRevision = Math.max(0L, oreRevision);
        settingsRevision = Math.max(0L, settingsRevision);
        terrainMode = terrainMode == null ? TerrainMode.FLAT : terrainMode;
        orePreset = orePreset == null ? OrePreset.VANILLA_BALANCED : orePreset;
        gameplay = gameplay == null ? GameplaySettings.fromPreset(GameplayPreset.SAFE) : gameplay;
        portal = portal == null ? PortalSettings.defaults() : portal;
        identity = identity == null ? WorldIdentitySettings.defaults() : identity;
        capabilities = capabilities == null ? AdminCapabilities.none() : capabilities;
        activeProfileId = cleanId(activeProfileId, "vanilla_balanced");
        profiles = limitedCopy(profiles, ProtocolLimits.MAX_PROFILES);
        backups = limitedCopy(backups, ProtocolLimits.MAX_BACKUPS);
        portalStatus = clean(
                portalStatus, AdminLocalizedMessage.encode("message.delvefold.admin.snapshot.portal_unavailable"));
        worldStatus = clean(
                worldStatus,
                initialized
                        ? AdminLocalizedMessage.encode("message.delvefold.admin.snapshot.world_ready")
                        : AdminLocalizedMessage.encode("message.delvefold.admin.snapshot.world_uninitialized"));
        pendingOperation = pendingOperation == null ? PendingOperation.NONE : pendingOperation;
        diagnostics = limitedStrings(diagnostics, ProtocolLimits.MAX_DIAGNOSTICS, ProtocolLimits.MESSAGE_LENGTH);
        oreRules = limitedCopy(oreRules, ProtocolLimits.MAX_ORE_RULES_PER_PAGE);
        oreRuleTotal = Math.max(oreRules.size(), Math.min(oreRuleTotal, ProtocolLimits.MAX_ORE_RULES));
        orePage = Math.min(ProtocolLimits.MAX_ORE_RULES, Math.max(0, orePage));
        this.oreRevision = oreRevision;
        this.settingsRevision = settingsRevision;
        this.backendReady = backendReady;
        this.initialized = initialized;
        this.terrainMode = terrainMode;
        this.orePreset = orePreset;
        this.gameplay = gameplay;
        this.portal = portal;
        this.identity = identity;
        this.capabilities = capabilities;
        this.activeProfileId = activeProfileId;
        this.profiles = profiles;
        this.backups = backups;
        this.portalStatus = portalStatus;
        this.worldStatus = worldStatus;
        this.pendingOperation = pendingOperation;
        this.diagnostics = diagnostics;
        this.oreRuleTotal = oreRuleTotal;
        this.orePage = orePage;
        this.oreRules = oreRules;
    }

    /**
     * Source-compatible constructor for protocol-11 callers that only knew whether some mining-world reset was pending.
     * Legacy {@code true} values represent the established delete/recreate operation; protocol 12 carries the explicit
     * operation kind.
     *
     * @param oreRevision authoritative ore-configuration revision
     * @param settingsRevision authoritative settings revision
     * @param backendReady whether the backend is available for mutation
     * @param initialized whether initial world configuration has been committed
     * @param terrainMode current terrain mode
     * @param orePreset current ore preset
     * @param gameplay current gameplay settings
     * @param portal current portal settings
     * @param identity current world identity settings
     * @param capabilities capabilities computed for the receiving player
     * @param activeProfileId active profile identifier
     * @param profiles profile summaries to copy
     * @param backups backup summaries to copy
     * @param portalStatus localized portal status
     * @param worldStatus localized world status
     * @param resetPending whether a legacy world reset is pending
     * @param diagnostics diagnostic strings to sanitize and copy
     * @param oreRuleTotal total authoritative ore-rule count
     * @param orePage zero-based rule page
     * @param oreRules rule page to copy
     * @throws NullPointerException if a copied profile, backup, or ore-rule list contains a null element
     */
    public AdminSnapshot(
            long oreRevision,
            long settingsRevision,
            boolean backendReady,
            boolean initialized,
            @Nullable TerrainMode terrainMode,
            @Nullable OrePreset orePreset,
            @Nullable GameplaySettings gameplay,
            @Nullable PortalSettings portal,
            @Nullable WorldIdentitySettings identity,
            @Nullable AdminCapabilities capabilities,
            @Nullable String activeProfileId,
            @Nullable List<ProfileDraft> profiles,
            @Nullable List<BackupDraft> backups,
            @Nullable String portalStatus,
            @Nullable String worldStatus,
            boolean resetPending,
            @Nullable List<@Nullable String> diagnostics,
            int oreRuleTotal,
            int orePage,
            @Nullable List<OreRuleDraft> oreRules) {
        this(
                oreRevision,
                settingsRevision,
                backendReady,
                initialized,
                terrainMode,
                orePreset,
                gameplay,
                portal,
                identity,
                capabilities,
                activeProfileId,
                profiles,
                backups,
                portalStatus,
                worldStatus,
                resetPending ? PendingOperation.WORLD_OPERATION : PendingOperation.NONE,
                diagnostics,
                oreRuleTotal,
                orePage,
                oreRules);
    }

    /**
     * Source-compatible constructor for callers predating explicit pending-operation kinds and paged ore-rule views.
     * The supplied rules become page zero and determine the total count.
     *
     * @param oreRevision authoritative ore-configuration revision
     * @param settingsRevision authoritative settings revision
     * @param backendReady whether the backend is available for mutation
     * @param initialized whether initial world configuration has been committed
     * @param terrainMode current terrain mode
     * @param orePreset current ore preset
     * @param gameplay current gameplay settings
     * @param portal current portal settings
     * @param identity current world identity settings
     * @param capabilities capabilities computed for the receiving player
     * @param activeProfileId active profile identifier
     * @param profiles profile summaries to copy
     * @param backups backup summaries to copy
     * @param portalStatus localized portal status
     * @param worldStatus localized world status
     * @param resetPending whether a legacy world reset is pending
     * @param diagnostics diagnostic strings to sanitize and copy
     * @param oreRules first-page rules to copy; {@code null} produces an empty page
     * @throws NullPointerException if a copied profile, backup, or ore-rule list contains a null element
     */
    public AdminSnapshot(
            long oreRevision,
            long settingsRevision,
            boolean backendReady,
            boolean initialized,
            @Nullable TerrainMode terrainMode,
            @Nullable OrePreset orePreset,
            @Nullable GameplaySettings gameplay,
            @Nullable PortalSettings portal,
            @Nullable WorldIdentitySettings identity,
            @Nullable AdminCapabilities capabilities,
            @Nullable String activeProfileId,
            @Nullable List<ProfileDraft> profiles,
            @Nullable List<BackupDraft> backups,
            @Nullable String portalStatus,
            @Nullable String worldStatus,
            boolean resetPending,
            @Nullable List<@Nullable String> diagnostics,
            @Nullable List<OreRuleDraft> oreRules) {
        this(
                oreRevision,
                settingsRevision,
                backendReady,
                initialized,
                terrainMode,
                orePreset,
                gameplay,
                portal,
                identity,
                capabilities,
                activeProfileId,
                profiles,
                backups,
                portalStatus,
                worldStatus,
                resetPending ? PendingOperation.WORLD_OPERATION : PendingOperation.NONE,
                diagnostics,
                oreRules == null ? 0 : oreRules.size(),
                0,
                oreRules);
    }

    /**
     * Creates the deterministic, immutable snapshot returned when no administration backend is installed.
     *
     * @return unavailable snapshot with zero revisions, safe defaults, no capabilities, and a diagnostic
     */
    public static AdminSnapshot unavailable() {
        return new AdminSnapshot(
                0L,
                0L,
                false,
                false,
                TerrainMode.FLAT,
                OrePreset.VANILLA_BALANCED,
                GameplaySettings.fromPreset(GameplayPreset.SAFE),
                PortalSettings.defaults(),
                WorldIdentitySettings.defaults(),
                AdminCapabilities.none(),
                "vanilla_balanced",
                List.of(),
                List.of(),
                AdminLocalizedMessage.encode("message.delvefold.admin.snapshot.backend_portal_disabled"),
                AdminLocalizedMessage.encode("message.delvefold.admin.snapshot.backend_unavailable"),
                PendingOperation.NONE,
                List.of(AdminLocalizedMessage.encode("message.delvefold.admin.snapshot.backend_diagnostic")),
                0,
                0,
                List.of());
    }

    /**
     * Returns a compact display-only revision; writes must use the applicable domain-specific revision instead.
     *
     * @return greater of the ore and settings revisions
     */
    public long revision() {
        return Math.max(this.oreRevision, this.settingsRevision);
    }

    /**
     * Provides the legacy aggregate pending-state view retained for GUI and integration callers.
     *
     * @return whether either a world operation or restore is pending
     */
    public boolean resetPending() {
        return pendingOperation != PendingOperation.NONE;
    }

    /**
     * Reports whether a delete or recreate operation is pending.
     *
     * @return whether the pending state includes a world operation
     */
    public boolean worldOperationPending() {
        return pendingOperation == PendingOperation.WORLD_OPERATION || pendingOperation == PendingOperation.BOTH;
    }

    /**
     * Reports whether a backup restore is pending.
     *
     * @return whether the pending state includes a restore
     */
    public boolean restorePending() {
        return pendingOperation == PendingOperation.RESTORE || pendingOperation == PendingOperation.BOTH;
    }

    /** The server-authoritative kind of restart-time mining-world mutation awaiting completion. */
    public enum PendingOperation {
        /** No restart-time world mutation is pending. */
        NONE,
        /** A mining-world delete or recreate operation is pending. */
        WORLD_OPERATION,
        /** A backup restore is pending. */
        RESTORE,
        /** Both lifecycle journals report pending work, preserving both recovery paths defensively. */
        BOTH;

        /**
         * Resolves the two lifecycle services without losing either cancellation path. BOTH is a defensive
         * representation for legacy or externally-corrupted saves containing both journals; each administration screen
         * can then expose its matching recovery action.
         *
         * @param worldOperationPending whether the world-operation journal has pending work
         * @param restorePending whether the restore journal has pending work
         * @return combined pending-operation representation
         */
        public static PendingOperation resolve(boolean worldOperationPending, boolean restorePending) {
            if (worldOperationPending && restorePending) {
                return BOTH;
            }
            if (restorePending) {
                return RESTORE;
            }
            return worldOperationPending ? WORLD_OPERATION : NONE;
        }
    }

    /**
     * Player-specific capabilities included for presentation and UI gating.
     *
     * <p>These flags do not authorize later requests; every handler and service method rechecks current permissions.
     *
     * @param canView whether the player may view administration state
     * @param canConfigure whether compatible configuration mutations are available
     * @param canManageWorld whether destructive world operations are available
     * @param canRestoreBackups whether backup restoration controls are available
     * @param canViewDiagnostics whether diagnostic details may be displayed
     */
    public record AdminCapabilities(
            boolean canView,
            boolean canConfigure,
            boolean canManageWorld,
            boolean canRestoreBackups,
            boolean canViewDiagnostics) {
        /**
         * Creates a capability set that grants no UI action or diagnostic access.
         *
         * @return immutable all-false capability set
         */
        public static AdminCapabilities none() {
            return new AdminCapabilities(false, false, false, false, false);
        }
    }

    /**
     * Immutable summary of one server-owned ore profile.
     *
     * @param id normalized, bounded profile identifier
     * @param builtIn whether the profile is shipped with the mod
     * @param localOverride whether a local profile shadows a built-in profile
     * @param ruleCount bounded number of rules in the profile
     * @param revision non-negative profile revision
     * @param valid whether server validation found no errors
     */
    public record ProfileDraft(
            String id, boolean builtIn, boolean localOverride, int ruleCount, long revision, boolean valid) {
        /**
         * Normalizes the identifier and clamps rule count and revision to protocol-safe non-negative values.
         *
         * @param id profile identifier, or {@code null} for the invalid fallback
         * @param builtIn whether the profile is built in
         * @param localOverride whether the profile is a local override
         * @param ruleCount number of rules to clamp
         * @param revision profile revision to clamp
         * @param valid whether the profile passed validation
         */
        public ProfileDraft {
            id = cleanId(id, "invalid");
            ruleCount = Math.max(0, Math.min(ruleCount, ProtocolLimits.MAX_ORE_RULES));
            revision = Math.max(0, revision);
        }
    }

    /**
     * Immutable summary of one server-owned world backup.
     *
     * @param id normalized, bounded backup identifier
     * @param createdAtEpochMillis non-negative creation time in epoch milliseconds
     * @param operation bounded description of the operation that created the backup
     * @param terrain bounded terrain description
     * @param sizeBytes backup size, or {@code -1} when unknown
     * @param pinned whether automatic cleanup must retain the backup
     * @param restorable whether current validation permits restoration
     * @param valid whether the catalog entry is structurally valid
     * @param manifestPresent whether the backup has an integrity manifest
     * @param verified whether integrity verification succeeded
     * @param legacy whether the backup predates manifests
     */
    public record BackupDraft(
            String id,
            long createdAtEpochMillis,
            String operation,
            String terrain,
            long sizeBytes,
            boolean pinned,
            boolean restorable,
            boolean valid,
            boolean manifestPresent,
            boolean verified,
            boolean legacy) {
        /**
         * Normalizes bounded text, creation time, and the unknown-size sentinel.
         *
         * @param id backup identifier, or {@code null} for the invalid fallback
         * @param createdAtEpochMillis creation time to clamp to a non-negative value
         * @param operation operation description, or {@code null} for the unknown fallback
         * @param terrain terrain description, or {@code null} for the unknown fallback
         * @param sizeBytes size to clamp to at least {@code -1}
         * @param pinned whether the backup is pinned
         * @param restorable whether the backup is restorable
         * @param valid whether the catalog entry is valid
         * @param manifestPresent whether an integrity manifest exists
         * @param verified whether integrity verification succeeded
         * @param legacy whether the backup predates manifests
         */
        public BackupDraft {
            id = cleanId(id, "invalid");
            operation = clean(operation, "unknown");
            terrain = clean(terrain, "unknown");
            createdAtEpochMillis = Math.max(0, createdAtEpochMillis);
            sizeBytes = Math.max(-1, sizeBytes);
        }

        /**
         * Source-compatible constructor for callers predating backup manifests; such entries are represented as legacy.
         *
         * @param id backup identifier
         * @param createdAtEpochMillis creation time in epoch milliseconds
         * @param operation operation description
         * @param terrain terrain description
         * @param sizeBytes backup size, or {@code -1} when unknown
         * @param pinned whether the backup is pinned
         * @param restorable whether the backup is restorable
         * @param valid whether the catalog entry is valid
         */
        public BackupDraft(
                String id,
                long createdAtEpochMillis,
                String operation,
                String terrain,
                long sizeBytes,
                boolean pinned,
                boolean restorable,
                boolean valid) {
            this(
                    id,
                    createdAtEpochMillis,
                    operation,
                    terrain,
                    sizeBytes,
                    pinned,
                    restorable,
                    valid,
                    false,
                    false,
                    true);
        }

        /**
         * Returns the compact display state derived from validity and manifest metadata.
         *
         * @return one of {@code invalid}, {@code legacy}, {@code verified}, or {@code unverified}
         */
        public String integrityState() {
            if (!valid) {
                return "invalid";
            }
            if (legacy) {
                return "legacy";
            }
            if (verified && restorable) {
                return "verified";
            }
            return manifestPresent ? "unverified" : "legacy";
        }
    }

    private static String clean(@Nullable String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.length() > ProtocolLimits.MESSAGE_LENGTH
                ? value.substring(0, ProtocolLimits.MESSAGE_LENGTH)
                : value;
    }

    private static <T> List<T> limitedCopy(@Nullable List<T> values, int maximum) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return List.copyOf(values.subList(0, Math.min(values.size(), maximum)));
    }

    private static List<String> limitedStrings(
            @Nullable List<@Nullable String> values, int maximumCount, int maximumLength) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .limit(maximumCount)
                .map(value -> {
                    String safe = value == null ? "" : value;
                    return safe.length() > maximumLength ? safe.substring(0, maximumLength) : safe;
                })
                .toList();
    }

    /**
     * Immutable, bounded client-editable representation of one ore rule.
     *
     * <p>List containers are defensively copied; registry identifiers and numeric semantics remain subject to
     * authoritative server validation before persistence.
     *
     * @param id normalized, bounded rule identifier
     * @param enabled whether generation is enabled
     * @param required whether absence of the target should invalidate configuration
     * @param primaryBlockId normalized primary block identifier
     * @param variants bounded, immutable target variants
     * @param terrainModes bounded, immutable applicable terrain modes
     * @param biomeIncludes bounded, immutable biome selector inclusions
     * @param biomeExcludes bounded, immutable biome selector exclusions
     * @param bands bounded, immutable placement bands
     */
    public record OreRuleDraft(
            String id,
            boolean enabled,
            boolean required,
            String primaryBlockId,
            List<OreVariantDraft> variants,
            List<TerrainMode> terrainModes,
            List<String> biomeIncludes,
            List<String> biomeExcludes,
            List<OreBandDraft> bands) {

        /**
         * Normalizes identifiers and takes protocol-bounded immutable copies of all list containers.
         *
         * @param id rule identifier, or {@code null} for the new-rule fallback
         * @param enabled whether generation is enabled
         * @param required whether the target is required
         * @param primaryBlockId primary block identifier, or {@code null} for the iron-ore fallback
         * @param variants target variants to copy and bound; {@code null} becomes empty
         * @param terrainModes terrain modes to copy and bound; {@code null} becomes empty
         * @param biomeIncludes biome inclusion selectors to sanitize and bound; {@code null} becomes empty
         * @param biomeExcludes biome exclusion selectors to sanitize and bound; {@code null} becomes empty
         * @param bands placement bands to copy and bound; {@code null} becomes empty
         * @throws NullPointerException if a copied variant, terrain-mode, or band list contains a null element
         */
        public OreRuleDraft {
            id = cleanId(id, "new_ore");
            primaryBlockId = cleanId(primaryBlockId, "minecraft:iron_ore");
            variants = limitedCopy(variants, ProtocolLimits.MAX_VARIANTS);
            terrainModes = limitedCopy(terrainModes, ProtocolLimits.MAX_TERRAIN_MODES);
            biomeIncludes = limitedStrings(
                    biomeIncludes, ProtocolLimits.MAX_BIOME_SELECTORS_PER_LIST, ProtocolLimits.ID_LENGTH + 1);
            biomeExcludes = limitedStrings(
                    biomeExcludes, ProtocolLimits.MAX_BIOME_SELECTORS_PER_LIST, ProtocolLimits.ID_LENGTH + 1);
            bands = limitedCopy(bands, ProtocolLimits.MAX_BANDS);
        }

        /**
         * Creates an enabled single-variant rule with all terrain modes, the default mining-biome selector, and one
         * default band.
         *
         * @param blockId namespaced primary block identifier; null or blank input uses iron ore
         * @param replaceTag replacement tag for the generated variant
         * @return immutable default rule draft
         * @throws IndexOutOfBoundsException if a nonblank normalized block identifier has no namespace separator
         */
        public static OreRuleDraft createDefault(String blockId, String replaceTag) {
            String normalizedBlock = cleanId(blockId, "minecraft:iron_ore");
            String path = normalizedBlock.substring(normalizedBlock.indexOf(':') + 1);
            String ruleId = normalizedBlock.substring(0, normalizedBlock.indexOf(':')) + "." + path;
            return new OreRuleDraft(
                    ruleId,
                    true,
                    false,
                    normalizedBlock,
                    List.of(new OreVariantDraft(normalizedBlock, "", replaceTag, java.util.Map.of())),
                    List.of(TerrainMode.values()),
                    List.of("#delvefold:mining_biomes"),
                    List.of(),
                    List.of(OreBandDraft.defaultBand()));
        }

        /**
         * Copies this rule while replacing only its enabled state.
         *
         * @param value new enabled state
         * @return normalized immutable rule draft with the requested state
         */
        public OreRuleDraft withEnabled(boolean value) {
            return new OreRuleDraft(
                    id, value, required, primaryBlockId, variants, terrainModes, biomeIncludes, biomeExcludes, bands);
        }
    }

    /**
     * Immutable target variant in an ore-rule draft.
     *
     * <p>The state map is copied into sorted, unmodifiable form and limited to the protocol property count.
     *
     * @param blockId normalized block identifier, mutually substitutable with {@code blockTag}
     * @param blockTag normalized block tag without a leading hash, mutually substitutable with {@code blockId}
     * @param replaceTag normalized replacement-target tag
     * @param state bounded, sorted, immutable block-state property map
     * @param weight generation selection weight within {@link #MIN_WEIGHT} and {@link #MAX_WEIGHT}
     */
    public record OreVariantDraft(
            String blockId, String blockTag, String replaceTag, java.util.Map<String, String> state, int weight) {
        /** Minimum accepted target-selection weight. */
        public static final int MIN_WEIGHT = OreTarget.MIN_WEIGHT;

        /** Maximum accepted target-selection weight. */
        public static final int MAX_WEIGHT = OreTarget.MAX_WEIGHT;

        /**
         * Normalizes source identifiers, replacement tag, and state ownership and validates the selection weight.
         *
         * @param blockId block identifier, or empty text to select by tag
         * @param blockTag block tag, optionally hash-prefixed, or empty text to select by identifier
         * @param replaceTag replacement-target tag, or {@code null} for the stone replacement fallback
         * @param state state properties to copy, sort, and bound; {@code null} becomes empty
         * @param weight target-selection weight
         * @throws IllegalArgumentException if the weight is outside the supported range
         * @throws NullPointerException if the state map contains a null key
         */
        public OreVariantDraft {
            blockId = blockId == null ? "" : blockId.trim();
            blockTag = blockTag == null ? "" : stripHash(blockTag.trim());
            if (blockId.isBlank() && blockTag.isBlank()) {
                blockId = "minecraft:iron_ore";
            }
            replaceTag = cleanId(replaceTag, "minecraft:stone_ore_replaceables");
            if (state == null || state.isEmpty()) {
                state = java.util.Map.of();
            } else {
                java.util.TreeMap<String, String> sanitized = new java.util.TreeMap<>();
                state.entrySet().stream()
                        .limit(ProtocolLimits.MAX_STATE_PROPERTIES)
                        .forEach(entry -> sanitized.put(entry.getKey(), entry.getValue()));
                state = java.util.Collections.unmodifiableMap(sanitized);
            }
            if (weight < MIN_WEIGHT || weight > MAX_WEIGHT) {
                throw new IllegalArgumentException(
                        "Ore target weight must be between " + MIN_WEIGHT + " and " + MAX_WEIGHT);
            }
        }

        /**
         * Source-compatible constructor for clients written before weighted targets, using {@link #MIN_WEIGHT}.
         *
         * @param blockId block identifier, or empty text to select by tag
         * @param blockTag block tag, optionally hash-prefixed, or empty text to select by identifier
         * @param replaceTag replacement-target tag
         * @param state state properties to copy, sort, and bound
         * @throws NullPointerException if the state map contains a null key
         */
        public OreVariantDraft(
                String blockId, String blockTag, String replaceTag, java.util.Map<String, String> state) {
            this(blockId, blockTag, replaceTag, state, MIN_WEIGHT);
        }

        /**
         * Returns the normalized source selector in identifier-or-hash-prefixed-tag form.
         *
         * @return block identifier when present, otherwise the block tag prefixed with {@code #}
         */
        public String sourceId() {
            return blockTag.isBlank() ? blockId : '#' + blockTag;
        }

        private static String stripHash(String value) {
            return value.startsWith("#") ? value.substring(1) : value;
        }
    }

    /**
     * Immutable placement-band draft whose numeric semantics are validated by the server before persistence.
     *
     * @param id normalized, bounded band identifier
     * @param veinSize proposed vein size
     * @param attemptsPerChunk proposed placement attempts per chunk
     * @param distribution height-distribution strategy
     * @param minY proposed minimum generation height
     * @param maxY proposed maximum generation height
     * @param peakY proposed triangular peak height
     * @param plateauMinY proposed trapezoid plateau minimum
     * @param plateauMaxY proposed trapezoid plateau maximum
     * @param discardOnAirExposure proposed discard probability
     * @param placement placement algorithm
     * @param province optional regional province settings; {@code null} means no province modulation
     */
    public record OreBandDraft(
            String id,
            int veinSize,
            double attemptsPerChunk,
            HeightDistribution distribution,
            int minY,
            int maxY,
            int peakY,
            int plateauMinY,
            int plateauMaxY,
            double discardOnAirExposure,
            OreBandPlacement placement,
            @Nullable ProvinceSettings province) {

        /**
         * Normalizes the identifier and supplies safe defaults for nullable distribution and placement values.
         *
         * @param id band identifier, or {@code null} for the main-band fallback
         * @param veinSize proposed vein size
         * @param attemptsPerChunk proposed placement attempts per chunk
         * @param distribution height distribution, or {@code null} for uniform
         * @param minY proposed minimum generation height
         * @param maxY proposed maximum generation height
         * @param peakY proposed triangular peak height
         * @param plateauMinY proposed trapezoid plateau minimum
         * @param plateauMaxY proposed trapezoid plateau maximum
         * @param discardOnAirExposure proposed discard probability
         * @param placement placement algorithm, or {@code null} for vein placement
         * @param province optional province settings; {@code null} is preserved to mean no province modulation
         */
        public OreBandDraft {
            id = cleanId(id, "main");
            distribution = distribution == null ? HeightDistribution.UNIFORM : distribution;
            placement = placement == null ? OreBandPlacement.VEIN : placement;
        }

        /**
         * Source-compatible constructor for clients written before placement modes and regional provinces.
         *
         * @param id band identifier
         * @param veinSize proposed vein size
         * @param attemptsPerChunk proposed placement attempts per chunk
         * @param distribution height-distribution strategy
         * @param minY proposed minimum generation height
         * @param maxY proposed maximum generation height
         * @param peakY proposed triangular peak height
         * @param plateauMinY proposed trapezoid plateau minimum
         * @param plateauMaxY proposed trapezoid plateau maximum
         * @param discardOnAirExposure proposed discard probability
         */
        public OreBandDraft(
                String id,
                int veinSize,
                double attemptsPerChunk,
                HeightDistribution distribution,
                int minY,
                int maxY,
                int peakY,
                int plateauMinY,
                int plateauMaxY,
                double discardOnAirExposure) {
            this(
                    id,
                    veinSize,
                    attemptsPerChunk,
                    distribution,
                    minY,
                    maxY,
                    peakY,
                    plateauMinY,
                    plateauMaxY,
                    discardOnAirExposure,
                    OreBandPlacement.VEIN,
                    null);
        }

        /**
         * Creates the immutable baseline vein band used by new ore-rule drafts.
         *
         * @return uniform, non-provincial default band
         */
        public static OreBandDraft defaultBand() {
            return new OreBandDraft(
                    "main",
                    8,
                    8.0D,
                    HeightDistribution.UNIFORM,
                    -64,
                    64,
                    0,
                    -16,
                    16,
                    0.0D,
                    OreBandPlacement.VEIN,
                    null);
        }
    }

    private static String cleanId(@Nullable String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.length() > ProtocolLimits.ID_LENGTH ? trimmed.substring(0, ProtocolLimits.ID_LENGTH) : trimmed;
    }
}
