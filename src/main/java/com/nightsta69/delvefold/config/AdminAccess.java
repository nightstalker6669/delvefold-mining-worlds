package com.nightsta69.delvefold.config;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * Shared server-side authorization for commands and network payload handlers.
 *
 * <p>Player checks use NeoForge permission nodes and explicitly recognize the integrated-server owner for
 * administrative actions. Non-player command sources use vanilla operator levels. Callers must still validate request
 * contents, revisions, and lifecycle confirmation tokens after authorization.
 */
public final class AdminAccess {
    /** Vanilla operator fallback level for non-lifecycle configuration changes. */
    public static final int CONFIGURE_PERMISSION = 2;
    /** Vanilla operator fallback level for destructive or lifecycle world-management actions. */
    public static final int WORLD_MANAGEMENT_PERMISSION = 4;

    private AdminAccess() {}

    /**
     * Checks whether a command source may inspect and mutate ordinary Delvefold configuration.
     *
     * @param source server command source
     * @return {@code true} for an authorized player or a non-player source at operator level 2 or higher
     */
    public static boolean canConfigure(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player
                ? canConfigure(player)
                : source.hasPermission(CONFIGURE_PERMISSION);
    }

    /**
     * Checks whether a command source may perform lifecycle, backup, renewal, or protected-hub actions.
     *
     * @param source server command source
     * @return {@code true} for an authorized player or a non-player source at operator level 4 or higher
     */
    public static boolean canManageWorld(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player
                ? canManageWorld(player)
                : source.hasPermission(WORLD_MANAGEMENT_PERMISSION);
    }

    /**
     * Checks a player against the configure node, including the integrated-server owner override.
     *
     * @param player connected server player
     * @return {@code true} when the player may perform ordinary configuration changes
     */
    public static boolean canConfigure(ServerPlayer player) {
        return player.getServer().isSingleplayerOwner(player.getGameProfile())
                || DelvefoldPermissions.granted(player, DelvefoldPermissions.CONFIGURE);
    }

    /**
     * Checks a player against the world-management node, including the integrated-server owner override.
     *
     * @param player connected server player
     * @return {@code true} when the player may perform lifecycle and backup actions
     */
    public static boolean canManageWorld(ServerPlayer player) {
        return player.getServer().isSingleplayerOwner(player.getGameProfile())
                || DelvefoldPermissions.granted(player, DelvefoldPermissions.MANAGE_WORLD);
    }

    /**
     * Checks whether a player may ignite and travel through Delvefold portals.
     *
     * <p>The node defaults to allowed, but a permission provider may deny it. Administrative owner overrides do not
     * bypass an explicit portal denial.
     *
     * @param player connected server player
     * @return current permission-provider decision for the portal-use node
     */
    public static boolean canUsePortal(ServerPlayer player) {
        return DelvefoldPermissions.granted(player, DelvefoldPermissions.USE_PORTAL);
    }
}
