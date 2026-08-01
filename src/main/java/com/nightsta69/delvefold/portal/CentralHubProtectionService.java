package com.nightsta69.delvefold.portal;

import com.nightsta69.delvefold.config.AdminAccess;
import com.nightsta69.delvefold.config.model.PortalHubSettings;
import com.nightsta69.delvefold.config.model.PortalRoutingMode;
import com.nightsta69.delvefold.config.model.PortalSettings;
import com.nightsta69.delvefold.world.DelvefoldWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Stateless protection policy for the vertical column surrounding a configured central hub. */
final class CentralHubProtectionService {
    private CentralHubProtectionService() {}

    static boolean isProtected(ServerLevel level, BlockPos position, PortalSettings settings) {
        return isProtected(position, settings, DelvefoldWorldgen.isMiningLevel(level.dimension()));
    }

    static boolean isProtected(BlockPos position, PortalSettings settings, boolean miningLevel) {
        return settings != null
                && settings.routingMode() == PortalRoutingMode.CENTRAL_HUB
                && miningLevel
                && isInsideRadius(position, settings.hub());
    }

    static boolean mayModify(ServerPlayer player, ServerLevel level, BlockPos position, PortalSettings settings) {
        return modificationAllowed(isProtected(level, position, settings), AdminAccess.canManageWorld(player));
    }

    static boolean mayModify(ServerPlayer player, BlockPos position, PortalSettings settings, boolean miningLevel) {
        return modificationAllowed(isProtected(position, settings, miningLevel), AdminAccess.canManageWorld(player));
    }

    static boolean modificationAllowed(boolean protectedPosition, boolean hasWorldManagementPermission) {
        return !protectedPosition || hasWorldManagementPermission;
    }

    static boolean isInsideRadius(BlockPos position, PortalHubSettings hub) {
        return isInsideRadius(position.getX(), position.getZ(), hub);
    }

    static boolean isInsideRadius(int x, int z, PortalHubSettings hub) {
        long dx = (long) x - hub.x();
        long dz = (long) z - hub.z();
        long radius = hub.protectionRadius();
        return dx * dx + dz * dz <= radius * radius;
    }
}
