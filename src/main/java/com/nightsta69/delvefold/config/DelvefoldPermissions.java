package com.nightsta69.delvefold.config;

import com.nightsta69.delvefold.Delvefold;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

/** Permission nodes understood by NeoForge permission handlers such as LuckPerms bridges. */
@SuppressWarnings("unchecked")
public final class DelvefoldPermissions {
    public static final PermissionNode<Boolean> CONFIGURE = node(
            "configure", AdminAccess.CONFIGURE_PERMISSION,
            "permission.delvefold.configure.name", "permission.delvefold.configure.description");
    public static final PermissionNode<Boolean> MANAGE_WORLD = node(
            "manage_world", AdminAccess.WORLD_MANAGEMENT_PERMISSION,
            "permission.delvefold.manage_world.name", "permission.delvefold.manage_world.description");
    public static final PermissionNode<Boolean> USE_PORTAL = new PermissionNode<>(
            Delvefold.MOD_ID, "use_portal", PermissionTypes.BOOLEAN,
            (player, uuid, contexts) -> true)
            .setInformation(Component.translatable("permission.delvefold.use_portal.name"),
                    Component.translatable("permission.delvefold.use_portal.description"));

    private DelvefoldPermissions() {
    }

    public static void onGatherNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(CONFIGURE, MANAGE_WORLD, USE_PORTAL);
    }

    private static PermissionNode<Boolean> node(
            String name, int fallbackLevel, String title, String description) {
        return new PermissionNode<>(Delvefold.MOD_ID, name, PermissionTypes.BOOLEAN,
                (player, uuid, contexts) -> player != null && player.hasPermissions(fallbackLevel))
                .setInformation(Component.translatable(title), Component.translatable(description));
    }

    public static boolean granted(ServerPlayer player, PermissionNode<Boolean> node) {
        return net.neoforged.neoforge.server.permission.PermissionAPI.getPermission(player, node);
    }
}
