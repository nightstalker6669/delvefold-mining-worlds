package com.nightsta69.delvefold.config;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/** Shared authorization for commands and network payload handlers. */
public final class AdminAccess {
    public static final int CONFIGURE_PERMISSION = 2;
    public static final int WORLD_MANAGEMENT_PERMISSION = 4;

    private AdminAccess() {}

    public static boolean canConfigure(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player
                ? canConfigure(player)
                : source.hasPermission(CONFIGURE_PERMISSION);
    }

    public static boolean canManageWorld(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player
                ? canManageWorld(player)
                : source.hasPermission(WORLD_MANAGEMENT_PERMISSION);
    }

    public static boolean canConfigure(ServerPlayer player) {
        return player.getServer().isSingleplayerOwner(player.getGameProfile())
                || DelvefoldPermissions.granted(player, DelvefoldPermissions.CONFIGURE);
    }

    public static boolean canManageWorld(ServerPlayer player) {
        return player.getServer().isSingleplayerOwner(player.getGameProfile())
                || DelvefoldPermissions.granted(player, DelvefoldPermissions.MANAGE_WORLD);
    }

    public static boolean canUsePortal(ServerPlayer player) {
        return DelvefoldPermissions.granted(player, DelvefoldPermissions.USE_PORTAL);
    }
}
