package com.nightsta69.delvefold.network.service;

import com.nightsta69.delvefold.config.model.GameplaySettings;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.PortalSettings;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.network.model.AdminOperation;
import com.nightsta69.delvefold.network.model.AdminSnapshot;
import com.nightsta69.delvefold.network.model.ProfileOperation;
import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;

/** Thread-safe service locator installed once by the common server backend. */
public final class DelvefoldAdminServices {
    private static final DelvefoldAdminService UNAVAILABLE = new UnavailableService();
    private static volatile DelvefoldAdminService service = UNAVAILABLE;

    private DelvefoldAdminServices() {
    }

    public static DelvefoldAdminService get() {
        return service;
    }

    public static void install(DelvefoldAdminService implementation) {
        service = Objects.requireNonNull(implementation, "implementation");
    }

    public static void clearForTests() {
        service = UNAVAILABLE;
    }

    private static final class UnavailableService implements DelvefoldAdminService {
        @Override
        public AdminSnapshot snapshot(ServerPlayer player, int orePage, int orePageSize) {
            return AdminSnapshot.unavailable();
        }

        @Override
        public ServiceResult initialize(ServerPlayer player, long expectedOreRevision, long expectedSettingsRevision, TerrainMode terrainMode,
                OrePreset orePreset, GameplaySettings gameplay) {
            return ServiceResult.unavailable(Math.max(expectedOreRevision, expectedSettingsRevision));
        }

        @Override
        public ServiceResult saveOreRule(
                ServerPlayer player,
                long expectedRevision,
                AdminSnapshot.OreRuleDraft rule,
                boolean createOnly) {
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
        public ServiceResult performProfile(ServerPlayer player, long expectedOreRevision, ProfileOperation operation,
                String sourceId, String targetId, String json, boolean overwrite) {
            return ServiceResult.unavailable(expectedOreRevision);
        }

        @Override
        public ServiceResult perform(ServerPlayer player, long expectedRevision, AdminOperation operation,
                String confirmation) {
            return ServiceResult.unavailable(expectedRevision);
        }
    }
}
