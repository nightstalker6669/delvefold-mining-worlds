package com.nightsta69.delvefold.network.service;

import com.nightsta69.delvefold.admin.AdminLocalizedMessage;
import com.nightsta69.delvefold.config.model.GameplaySettings;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.PortalSettings;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.WorldIdentitySettings;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast;
import com.nightsta69.delvefold.network.model.ActionStatus;
import com.nightsta69.delvefold.network.model.AdminOperation;
import com.nightsta69.delvefold.network.model.AdminSnapshot;
import com.nightsta69.delvefold.network.model.BackupOperation;
import com.nightsta69.delvefold.network.model.ProfileOperation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side integration boundary for the GUI and command/config backend.
 * Implementations must re-check authorization, validate every registry id and
 * numeric bound, compare expected revisions atomically, and only then persist.
 */
public interface DelvefoldAdminService {
    AdminSnapshot snapshot(ServerPlayer player, int orePage, int orePageSize);

    OreProfileForecast forecast(ServerPlayer player, String profileId, int page);

    default AdminSnapshot snapshot(ServerPlayer player) {
        return snapshot(player, 0, com.nightsta69.delvefold.network.ProtocolLimits.GUI_ORE_RULES_PER_PAGE);
    }

    ServiceResult initialize(
            ServerPlayer player,
            long expectedOreRevision,
            long expectedSettingsRevision,
            TerrainMode terrainMode,
            OrePreset orePreset,
            GameplaySettings gameplay,
            WorldIdentitySettings identity);

    ServiceResult saveOreRule(
            ServerPlayer player,
            long expectedRevision,
            AdminSnapshot.OreRuleDraft rule,
            boolean createOnly);

    ServiceResult deleteOreRule(ServerPlayer player, long expectedRevision, String ruleId);

    ServiceResult updateGameplay(
            ServerPlayer player,
            long expectedRevision,
            GameplaySettings gameplay);

    ServiceResult updatePortal(ServerPlayer player, long expectedRevision, PortalSettings portal);

    ServiceResult updateIdentity(ServerPlayer player, long expectedRevision, WorldIdentitySettings identity);

    ServiceResult performProfile(
            ServerPlayer player,
            long expectedOreRevision,
            ProfileOperation operation,
            String sourceId,
            String targetId,
            String json,
            boolean overwrite);

    ServiceResult performBackup(
            ServerPlayer player, long expectedSettingsRevision, BackupOperation operation, String backupId);

    ServiceResult perform(
            ServerPlayer player,
            long expectedRevision,
            AdminOperation operation,
            String confirmation);

    record ServiceResult(ActionStatus status, long revision, String message, boolean refreshSnapshot) {
        public ServiceResult {
            status = status == null ? ActionStatus.ERROR : status;
            revision = Math.max(0L, revision);
            message = message == null || message.isBlank()
                    ? AdminLocalizedMessage.encode("message.delvefold.admin.operation_completed")
                    : message;
        }

        public static ServiceResult unavailable(long revision) {
            return new ServiceResult(ActionStatus.REJECTED, revision,
                    AdminLocalizedMessage.encode("message.delvefold.admin.backend_unavailable"), false);
        }
    }
}
