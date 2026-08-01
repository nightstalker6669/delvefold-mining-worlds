package com.nightsta69.delvefold.network.service;

import com.nightsta69.delvefold.config.analysis.OreProfileForecast;
import com.nightsta69.delvefold.config.model.GameplaySettings;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.PortalSettings;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.network.model.AdminOperation;
import com.nightsta69.delvefold.network.model.AdminSnapshot;
import com.nightsta69.delvefold.network.model.BackupOperation;
import com.nightsta69.delvefold.network.model.ProfileOperation;
import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;

/** Process-wide service locator whose volatile backend publication is safe for concurrent readers. */
public final class DelvefoldAdminServices {
    private static final DelvefoldAdminService UNAVAILABLE = new UnavailableService();
    private static volatile DelvefoldAdminService service = UNAVAILABLE;

    private DelvefoldAdminServices() {}

    /**
     * Returns the currently installed backend or the non-null unavailable fallback.
     *
     * @return process-wide service visible to the calling thread
     */
    public static DelvefoldAdminService get() {
        return service;
    }

    /**
     * Publishes a process-wide administration backend to subsequent readers.
     *
     * @param implementation non-null backend to install
     * @throws NullPointerException if the implementation is {@code null}
     */
    public static void install(DelvefoldAdminService implementation) {
        service = Objects.requireNonNull(implementation, "implementation");
    }

    /** Resets the process-wide locator to its deterministic unavailable fallback for test isolation. */
    public static void clearForTests() {
        service = UNAVAILABLE;
    }

    private static final class UnavailableService implements DelvefoldAdminService {
        @Override
        public AdminSnapshot snapshot(ServerPlayer player, int orePage, int orePageSize) {
            return AdminSnapshot.unavailable();
        }

        @Override
        public OreProfileForecast forecast(ServerPlayer player, String profileId, int page) {
            throw new IllegalStateException("Delvefold administration backend is not installed");
        }

        @Override
        public ServiceResult initialize(
                ServerPlayer player,
                long expectedOreRevision,
                long expectedSettingsRevision,
                TerrainMode terrainMode,
                OrePreset orePreset,
                GameplaySettings gameplay,
                com.nightsta69.delvefold.config.model.WorldIdentitySettings identity) {
            return ServiceResult.unavailable(Math.max(expectedOreRevision, expectedSettingsRevision));
        }

        @Override
        public ServiceResult saveOreRule(
                ServerPlayer player, long expectedRevision, AdminSnapshot.OreRuleDraft rule, boolean createOnly) {
            return ServiceResult.unavailable(expectedRevision);
        }

        @Override
        public ServiceResult deleteOreRule(ServerPlayer player, long expectedRevision, String ruleId) {
            return ServiceResult.unavailable(expectedRevision);
        }

        @Override
        public ServiceResult updateGameplay(ServerPlayer player, long expectedRevision, GameplaySettings gameplay) {
            return ServiceResult.unavailable(expectedRevision);
        }

        @Override
        public ServiceResult updatePortal(ServerPlayer player, long expectedRevision, PortalSettings portal) {
            return ServiceResult.unavailable(expectedRevision);
        }

        @Override
        public ServiceResult updateIdentity(
                ServerPlayer player,
                long expectedRevision,
                com.nightsta69.delvefold.config.model.WorldIdentitySettings identity) {
            return ServiceResult.unavailable(expectedRevision);
        }

        @Override
        public ServiceResult performProfile(
                ServerPlayer player,
                long expectedOreRevision,
                ProfileOperation operation,
                String sourceId,
                String targetId,
                String json,
                boolean overwrite) {
            return ServiceResult.unavailable(expectedOreRevision);
        }

        @Override
        public ServiceResult performBackup(
                ServerPlayer player, long expectedSettingsRevision, BackupOperation operation, String backupId) {
            return ServiceResult.unavailable(expectedSettingsRevision);
        }

        @Override
        public ServiceResult perform(
                ServerPlayer player, long expectedRevision, AdminOperation operation, String confirmation) {
            return ServiceResult.unavailable(expectedRevision);
        }
    }
}
