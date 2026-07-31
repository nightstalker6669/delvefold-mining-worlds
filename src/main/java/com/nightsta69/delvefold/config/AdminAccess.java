package com.nightsta69.delvefold.config;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/** Shared authorization for commands and network payload handlers. */
public final class AdminAccess {
    public static final int CONFIGURE_PERMISSION = 2;
    public static final int WORLD_MANAGEMENT_PERMISSION = 4;

    private AdminAccess() {
    }

    public static boolean canConfigure(CommandSourceStack source) {
        return source.hasPermission(CONFIGURE_PERMISSION) || isIntegratedOwner(source);
    }

    public static boolean canManageWorld(CommandSourceStack source) {
        return source.hasPermission(WORLD_MANAGEMENT_PERMISSION) || isIntegratedOwner(source);
    }

    public static boolean canConfigure(ServerPlayer player) {
        return player.hasPermissions(CONFIGURE_PERMISSION)
                || player.getServer().isSingleplayerOwner(player.getGameProfile());
    }

    public static boolean canManageWorld(ServerPlayer player) {
        return player.hasPermissions(WORLD_MANAGEMENT_PERMISSION)
                || player.getServer().isSingleplayerOwner(player.getGameProfile());
    }

    private static boolean isIntegratedOwner(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player
                && source.getServer().isSingleplayerOwner(player.getGameProfile());
    }
}
