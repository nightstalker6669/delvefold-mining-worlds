package com.nightsta69.delvefold.network.service;

import com.nightsta69.delvefold.admin.AdminLocalizedMessage;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast;
import com.nightsta69.delvefold.config.model.GameplaySettings;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.PortalSettings;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.WorldIdentitySettings;
import com.nightsta69.delvefold.network.model.ActionStatus;
import com.nightsta69.delvefold.network.model.AdminOperation;
import com.nightsta69.delvefold.network.model.AdminSnapshot;
import com.nightsta69.delvefold.network.model.BackupOperation;
import com.nightsta69.delvefold.network.model.ProfileOperation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side integration boundary for the GUI and command/config backend. Implementations must re-check authorization,
 * validate every registry id and numeric bound, compare expected revisions atomically, and only then persist.
 *
 * <p>Returned snapshots and results are immutable values owned by the caller. An accepted result may either describe
 * completed work or acknowledge that explicitly asynchronous work started; such work publishes a later completion
 * result through the network integration.
 */
public interface DelvefoldAdminService {
    /**
     * Builds a bounded authoritative snapshot for one ore-rule page.
     *
     * @param player acting server player whose configure permission and capabilities are evaluated
     * @param orePage requested zero-based ore-rule page; implementations may clamp it to available pages
     * @param orePageSize requested page size; implementations bound it to the protocol maximum
     * @return immutable snapshot owned by the caller
     * @throws SecurityException if the player lacks configure permission
     */
    AdminSnapshot snapshot(ServerPlayer player, int orePage, int orePageSize);

    /**
     * Computes one bounded page of an ore profile forecast from authoritative configuration.
     *
     * @param player acting server player whose configure permission is rechecked
     * @param profileId profile identifier to resolve
     * @param page requested zero-based forecast page
     * @return immutable server-computed forecast page
     * @throws SecurityException if the player lacks configure permission
     * @throws IllegalStateException if the forecast cannot be loaded or computed
     */
    OreProfileForecast forecast(ServerPlayer player, String profileId, int page);

    /**
     * Builds the initial administration snapshot using the GUI protocol's default ore-rule page size.
     *
     * @param player acting server player whose configure permission and capabilities are evaluated
     * @return immutable first-page snapshot owned by the caller
     * @throws SecurityException if the player lacks configure permission
     */
    default AdminSnapshot snapshot(ServerPlayer player) {
        return snapshot(player, 0, com.nightsta69.delvefold.network.ProtocolLimits.GUI_ORE_RULES_PER_PAGE);
    }

    /**
     * Initializes the mining-world configuration after atomically checking both domain revisions.
     *
     * @param player acting server player whose configure permission is rechecked
     * @param expectedOreRevision ore revision observed by the caller
     * @param expectedSettingsRevision settings revision observed by the caller
     * @param terrainMode requested initial terrain mode
     * @param orePreset requested initial ore preset
     * @param gameplay requested initial gameplay settings
     * @param identity requested initial world identity
     * @return immutable outcome, using {@link ActionStatus#STALE} if either revision lost its race
     * @throws SecurityException if the player lacks configure permission
     */
    ServiceResult initialize(
            ServerPlayer player,
            long expectedOreRevision,
            long expectedSettingsRevision,
            TerrainMode terrainMode,
            OrePreset orePreset,
            GameplaySettings gameplay,
            WorldIdentitySettings identity);

    /**
     * Creates or replaces an ore rule after server-side validation and an atomic ore-revision check.
     *
     * @param player acting server player whose configure permission is rechecked
     * @param expectedRevision ore revision observed by the caller
     * @param rule immutable draft to validate and persist
     * @param createOnly whether an existing identifier must cause rejection instead of replacement
     * @return immutable operation outcome and authoritative resulting revision
     * @throws SecurityException if the player lacks configure permission
     */
    ServiceResult saveOreRule(
            ServerPlayer player, long expectedRevision, AdminSnapshot.OreRuleDraft rule, boolean createOnly);

    /**
     * Deletes an ore rule after atomically checking the ore revision.
     *
     * @param player acting server player whose configure permission is rechecked
     * @param expectedRevision ore revision observed by the caller
     * @param ruleId identifier of the rule to delete
     * @return immutable operation outcome and authoritative resulting revision
     * @throws SecurityException if the player lacks configure permission
     */
    ServiceResult deleteOreRule(ServerPlayer player, long expectedRevision, String ruleId);

    /**
     * Replaces gameplay settings after an atomic settings-revision check.
     *
     * @param player acting server player whose configure permission is rechecked
     * @param expectedRevision settings revision observed by the caller
     * @param gameplay immutable settings to validate and persist
     * @return immutable operation outcome and authoritative resulting revision
     * @throws SecurityException if the player lacks configure permission
     */
    ServiceResult updateGameplay(ServerPlayer player, long expectedRevision, GameplaySettings gameplay);

    /**
     * Replaces portal settings after an atomic settings-revision check.
     *
     * <p>Implementations may require world-management permission for sensitive subfields such as the hub definition.
     *
     * @param player acting server player whose permissions are rechecked
     * @param expectedRevision settings revision observed by the caller
     * @param portal immutable portal settings to validate and persist
     * @return immutable operation outcome and authoritative resulting revision
     * @throws SecurityException if the player lacks configure permission
     */
    ServiceResult updatePortal(ServerPlayer player, long expectedRevision, PortalSettings portal);

    /**
     * Replaces world identity settings after an atomic settings-revision check.
     *
     * <p>Implementations enforce initialization locks and may require world-management permission for renewal policy.
     *
     * @param player acting server player whose permissions are rechecked
     * @param expectedRevision settings revision observed by the caller
     * @param identity immutable identity settings to validate and persist
     * @return immutable operation outcome and authoritative resulting revision
     * @throws SecurityException if the player lacks configure permission
     */
    ServiceResult updateIdentity(ServerPlayer player, long expectedRevision, WorldIdentitySettings identity);

    /**
     * Performs an operation on server-owned ore profiles.
     *
     * @param player acting server player whose configure permission is rechecked
     * @param expectedOreRevision ore revision observed by the caller
     * @param operation profile operation to execute
     * @param sourceId source profile identifier when required by the operation
     * @param targetId target profile identifier when required by the operation
     * @param json bounded profile JSON for clipboard import, or empty text otherwise
     * @param overwrite whether an existing target may be replaced
     * @return immutable operation outcome and authoritative resulting revision
     * @throws SecurityException if the player lacks configure permission
     */
    ServiceResult performProfile(
            ServerPlayer player,
            long expectedOreRevision,
            ProfileOperation operation,
            String sourceId,
            String targetId,
            String json,
            boolean overwrite);

    /**
     * Performs a world-backup catalog or restore operation after an atomic settings-revision check.
     *
     * <p>For asynchronous verification and deletion, an accepted return value means work started; the backend sends a
     * later completion result if the player remains online.
     *
     * @param player acting server player whose world-management permission is rechecked
     * @param expectedSettingsRevision settings revision observed by the caller
     * @param operation backup operation to execute
     * @param backupId server-owned backup identifier when required by the operation
     * @return immutable immediate outcome, which may acknowledge asynchronous start
     * @throws SecurityException if the player lacks world-management permission
     */
    ServiceResult performBackup(
            ServerPlayer player, long expectedSettingsRevision, BackupOperation operation, String backupId);

    /**
     * Performs a general administration operation after operation-specific authorization and revision checks.
     *
     * @param player acting server player whose required permission is rechecked
     * @param expectedRevision settings revision observed by the caller
     * @param operation requested administration operation
     * @param confirmation confirmation text for destructive operations
     * @return immutable outcome and revision associated with the operation
     * @throws SecurityException if the player lacks the operation's required permission
     */
    ServiceResult perform(ServerPlayer player, long expectedRevision, AdminOperation operation, String confirmation);

    /**
     * Immutable server-side result consumed by the network layer.
     *
     * <p>The revision is the authoritative domain revision associated with the outcome. {@code refreshSnapshot}
     * requests a subsequent full snapshot; it does not itself indicate success. For an asynchronous start,
     * {@link ActionStatus#ACCEPTED} may be followed by a separate completion result.
     *
     * @param status server-assigned disposition; {@code null} is normalized to {@link ActionStatus#ERROR}
     * @param revision associated domain revision, normalized to a non-negative value
     * @param message localized client-displayable representation; null or blank input receives a default message
     * @param refreshSnapshot whether the network layer should send a fresh authoritative snapshot
     */
    record ServiceResult(ActionStatus status, long revision, String message, boolean refreshSnapshot) {
        /**
         * Normalizes nullable status and message inputs and clamps the revision to a non-negative value.
         *
         * @param status server-assigned disposition, or {@code null}
         * @param revision associated domain revision
         * @param message client-displayable result message, or {@code null}
         * @param refreshSnapshot whether a fresh authoritative snapshot should follow
         */
        public ServiceResult {
            status = status == null ? ActionStatus.ERROR : status;
            revision = Math.max(0L, revision);
            message = message == null || message.isBlank()
                    ? AdminLocalizedMessage.encode("message.delvefold.admin.operation_completed")
                    : message;
        }

        /**
         * Creates a rejection result used while no administration backend is installed.
         *
         * @param revision caller's associated revision, normalized by the canonical constructor
         * @return unavailable rejection that does not request a snapshot refresh
         */
        public static ServiceResult unavailable(long revision) {
            return new ServiceResult(
                    ActionStatus.REJECTED,
                    revision,
                    AdminLocalizedMessage.encode("message.delvefold.admin.backend_unavailable"),
                    false);
        }
    }
}
