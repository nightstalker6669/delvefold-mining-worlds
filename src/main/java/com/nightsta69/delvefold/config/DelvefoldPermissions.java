package com.nightsta69.delvefold.config;

import com.nightsta69.delvefold.Delvefold;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

/** Permission nodes understood by NeoForge permission handlers such as LuckPerms bridges. */
public final class DelvefoldPermissions {
    /** Permission node for non-lifecycle configuration, with vanilla operator-level-2 fallback. */
    public static final PermissionNode<Boolean> CONFIGURE = node(
            "configure",
            AdminAccess.CONFIGURE_PERMISSION,
            "permission.delvefold.configure.name",
            "permission.delvefold.configure.description");
    /** Permission node for lifecycle and backup operations, with vanilla operator-level-4 fallback. */
    public static final PermissionNode<Boolean> MANAGE_WORLD = node(
            "manage_world",
            AdminAccess.WORLD_MANAGEMENT_PERMISSION,
            "permission.delvefold.manage_world.name",
            "permission.delvefold.manage_world.description");
    /** Portal ignition/travel permission node, allowed by default unless a permission provider denies it. */
    public static final PermissionNode<Boolean> USE_PORTAL = node(
            "use_portal",
            (player, uuid, contexts) -> true,
            "permission.delvefold.use_portal.name",
            "permission.delvefold.use_portal.description");

    private DelvefoldPermissions() {}

    /**
     * Registers every Delvefold permission node during NeoForge's node-gathering event.
     *
     * @param event server permission-node registration event
     */
    public static void onGatherNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(CONFIGURE, MANAGE_WORLD, USE_PORTAL);
    }

    private static PermissionNode<Boolean> node(String name, int fallbackLevel, String title, String description) {
        return node(
                name,
                (player, uuid, contexts) -> player != null && player.hasPermissions(fallbackLevel),
                title,
                description);
    }

    // NeoForge 21.1 exposes PermissionNode's dynamic-context varargs as a raw generic array.
    // Centralizing the zero-context constructor call keeps that unavoidable unchecked boundary to this method.
    @SuppressWarnings("unchecked")
    private static PermissionNode<Boolean> node(
            String name, PermissionNode.PermissionResolver<Boolean> defaultResolver, String title, String description) {
        return new PermissionNode<>(Delvefold.MOD_ID, name, PermissionTypes.BOOLEAN, defaultResolver)
                .setInformation(Component.translatable(title), Component.translatable(description));
    }

    /**
     * Resolves a Boolean permission node through NeoForge's current permission provider.
     *
     * @param player connected server player whose permission is requested
     * @param node Delvefold Boolean permission node
     * @return provider decision, including the node's configured fallback when no external provider overrides it
     */
    public static boolean granted(ServerPlayer player, PermissionNode<Boolean> node) {
        return net.neoforged.neoforge.server.permission.PermissionAPI.getPermission(player, node);
    }
}
