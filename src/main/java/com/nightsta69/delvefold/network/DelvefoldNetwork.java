package com.nightsta69.delvefold.network;

import com.mojang.logging.LogUtils;
import com.nightsta69.delvefold.config.AdminAccess;
import com.nightsta69.delvefold.network.model.ActionStatus;
import com.nightsta69.delvefold.network.model.AdminOperation;
import com.nightsta69.delvefold.network.model.AdminSnapshot;
import com.nightsta69.delvefold.network.payload.ActionResultPayload;
import com.nightsta69.delvefold.network.payload.AdminActionPayload;
import com.nightsta69.delvefold.network.payload.BackupActionPayload;
import com.nightsta69.delvefold.network.payload.DeleteOreRulePayload;
import com.nightsta69.delvefold.network.payload.GameplayUpdatePayload;
import com.nightsta69.delvefold.network.payload.InitializeWorldPayload;
import com.nightsta69.delvefold.network.payload.OpenGuiPayload;
import com.nightsta69.delvefold.network.payload.OpenGuiRequestPayload;
import com.nightsta69.delvefold.network.payload.PortalUpdatePayload;
import com.nightsta69.delvefold.network.payload.ProfileActionPayload;
import com.nightsta69.delvefold.network.payload.ProfileExportPayload;
import com.nightsta69.delvefold.network.payload.ProfileExportRequestPayload;
import com.nightsta69.delvefold.network.payload.OrePageRequestPayload;
import com.nightsta69.delvefold.network.payload.SaveOreRulePayload;
import com.nightsta69.delvefold.network.service.DelvefoldAdminService;
import com.nightsta69.delvefold.network.service.DelvefoldAdminServices;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

/** Common payload registration and server-authoritative request handlers. */
public final class DelvefoldNetwork {
    public static final String PROTOCOL_VERSION = "3";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile Consumer<OpenGuiPayload> clientOpenHandler = payload -> {
    };
    private static volatile Consumer<ActionResultPayload> clientResultHandler = payload -> {
    };
    private static volatile Consumer<ProfileExportPayload> clientProfileExportHandler = payload -> {
    };

    private DelvefoldNetwork() {
    }

    /** Call once from the mod constructor with the mod event bus. */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(DelvefoldNetwork::registerPayloadHandlers);
    }

    /**
     * Client bootstrap hook. Kept free of client-only types so this class is
     * safe to load on a dedicated server.
     */
    public static void installClientHandlers(
            Consumer<OpenGuiPayload> openHandler,
            Consumer<ActionResultPayload> resultHandler,
            Consumer<ProfileExportPayload> profileExportHandler) {
        clientOpenHandler = Objects.requireNonNull(openHandler, "openHandler");
        clientResultHandler = Objects.requireNonNull(resultHandler, "resultHandler");
        clientProfileExportHandler = Objects.requireNonNull(profileExportHandler, "profileExportHandler");
    }

    /** Server/command integration hook for /delvefold gui and config. */
    public static boolean openFor(ServerPlayer player) {
        if (!requirePermission(player, 2)) {
            return false;
        }
        sendSnapshot(player, 0);
        return true;
    }

    private static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(OpenGuiRequestPayload.TYPE, OpenGuiRequestPayload.STREAM_CODEC,
                DelvefoldNetwork::handleOpenRequest);
        registrar.playToServer(OrePageRequestPayload.TYPE, OrePageRequestPayload.STREAM_CODEC,
                DelvefoldNetwork::handleOrePageRequest);
        registrar.playToServer(InitializeWorldPayload.TYPE, InitializeWorldPayload.STREAM_CODEC,
                DelvefoldNetwork::handleInitialize);
        registrar.playToServer(SaveOreRulePayload.TYPE, SaveOreRulePayload.STREAM_CODEC,
                DelvefoldNetwork::handleSaveOreRule);
        registrar.playToServer(DeleteOreRulePayload.TYPE, DeleteOreRulePayload.STREAM_CODEC,
                DelvefoldNetwork::handleDeleteOreRule);
        registrar.playToServer(GameplayUpdatePayload.TYPE, GameplayUpdatePayload.STREAM_CODEC,
                DelvefoldNetwork::handleGameplayUpdate);
        registrar.playToServer(PortalUpdatePayload.TYPE, PortalUpdatePayload.STREAM_CODEC,
                DelvefoldNetwork::handlePortalUpdate);
        registrar.playToServer(ProfileActionPayload.TYPE, ProfileActionPayload.STREAM_CODEC,
                DelvefoldNetwork::handleProfileAction);
        registrar.playToServer(ProfileExportRequestPayload.TYPE, ProfileExportRequestPayload.STREAM_CODEC,
                DelvefoldNetwork::handleProfileExportRequest);
        registrar.playToServer(BackupActionPayload.TYPE, BackupActionPayload.STREAM_CODEC,
                DelvefoldNetwork::handleBackupAction);
        registrar.playToServer(AdminActionPayload.TYPE, AdminActionPayload.STREAM_CODEC,
                DelvefoldNetwork::handleAdminAction);

        registrar.playToClient(OpenGuiPayload.TYPE, OpenGuiPayload.STREAM_CODEC,
                (payload, context) -> clientOpenHandler.accept(payload));
        registrar.playToClient(ActionResultPayload.TYPE, ActionResultPayload.STREAM_CODEC,
                (payload, context) -> clientResultHandler.accept(payload));
        registrar.playToClient(ProfileExportPayload.TYPE, ProfileExportPayload.STREAM_CODEC,
                (payload, context) -> clientProfileExportHandler.accept(payload));
    }

    private static void handleOpenRequest(OpenGuiRequestPayload payload, IPayloadContext context) {
        ServerPlayer player = serverPlayer(context);
        if (player != null) {
            openFor(player);
        }
    }

    private static void handleOrePageRequest(OrePageRequestPayload payload, IPayloadContext context) {
        ServerPlayer player = authorizedPlayer(context, AdminAccess.CONFIGURE_PERMISSION);
        if (player != null) {
            sendSnapshot(player, payload.page());
        }
    }

    private static void handleInitialize(InitializeWorldPayload payload, IPayloadContext context) {
        ServerPlayer player = authorizedPlayer(context, 2);
        if (player == null) {
            return;
        }
        if (!payload.lockConfirmed()) {
            finish(player, new DelvefoldAdminService.ServiceResult(ActionStatus.REJECTED,
                    payload.expectedSettingsRevision(), "Initialization requires explicit lock confirmation.", false));
            return;
        }
        invoke(player, () -> DelvefoldAdminServices.get().initialize(
                player,
                payload.expectedOreRevision(),
                payload.expectedSettingsRevision(),
                payload.terrainMode(),
                payload.orePreset(),
                payload.gameplay()));
    }

    private static void handleSaveOreRule(SaveOreRulePayload payload, IPayloadContext context) {
        ServerPlayer player = authorizedPlayer(context, 2);
        if (player != null) {
            invoke(player, () -> DelvefoldAdminServices.get().saveOreRule(
                    player, payload.expectedRevision(), payload.rule(), payload.createOnly()));
        }
    }

    private static void handleDeleteOreRule(DeleteOreRulePayload payload, IPayloadContext context) {
        ServerPlayer player = authorizedPlayer(context, 2);
        if (player != null) {
            invoke(player, () -> DelvefoldAdminServices.get().deleteOreRule(
                    player, payload.expectedRevision(), payload.ruleId()));
        }
    }

    private static void handleGameplayUpdate(GameplayUpdatePayload payload, IPayloadContext context) {
        ServerPlayer player = authorizedPlayer(context, 2);
        if (player != null) {
            invoke(player, () -> DelvefoldAdminServices.get().updateGameplay(
                    player, payload.expectedRevision(), payload.gameplay()));
        }
    }

    private static void handlePortalUpdate(PortalUpdatePayload payload, IPayloadContext context) {
        ServerPlayer player = authorizedPlayer(context, 2);
        if (player != null) {
            invoke(player, () -> DelvefoldAdminServices.get().updatePortal(
                    player, payload.expectedRevision(), payload.portal()));
        }
    }

    private static void handleProfileAction(ProfileActionPayload payload, IPayloadContext context) {
        ServerPlayer player = authorizedPlayer(context, 2);
        if (player != null) {
            invoke(player, () -> DelvefoldAdminServices.get().performProfile(player,
                    payload.expectedOreRevision(), payload.operation(), payload.sourceId(), payload.targetId(),
                    payload.json(), payload.overwrite()));
        }
    }

    private static void handleProfileExportRequest(ProfileExportRequestPayload payload, IPayloadContext context) {
        ServerPlayer player = authorizedPlayer(context, 2);
        if (player == null) {
            return;
        }
        try {
            String json = com.nightsta69.delvefold.config.DelvefoldConfigService.get()
                    .exportProfileJson(payload.profileId());
            if (json.length() > ProtocolLimits.MAX_PROFILE_CLIPBOARD_CHARS) {
                finish(player, new DelvefoldAdminService.ServiceResult(ActionStatus.REJECTED,
                        com.nightsta69.delvefold.config.DelvefoldConfigService.get().snapshot().ores().revision(),
                        "Profile is too large for clipboard transfer; use /delvefold profile export instead.", false));
                return;
            }
            PacketDistributor.sendToPlayer(player, new ProfileExportPayload(payload.profileId(), json));
        } catch (IOException | IllegalArgumentException exception) {
            finish(player, new DelvefoldAdminService.ServiceResult(ActionStatus.ERROR, 0,
                    "Profile export failed: " + exception.getMessage(), false));
        }
    }

    private static void handleBackupAction(BackupActionPayload payload, IPayloadContext context) {
        ServerPlayer player = authorizedPlayer(context, AdminAccess.WORLD_MANAGEMENT_PERMISSION);
        if (player != null) {
            invoke(player, () -> DelvefoldAdminServices.get().performBackup(player,
                    payload.expectedSettingsRevision(), payload.operation(), payload.backupId()));
        }
    }

    private static void handleAdminAction(AdminActionPayload payload, IPayloadContext context) {
        AdminOperation operation = payload.operation();
        ServerPlayer player = authorizedPlayer(context, operation.permissionLevel());
        if (player == null) {
            return;
        }
        if (operation == AdminOperation.REFRESH) {
            sendSnapshot(player, 0);
            return;
        }
        if (!operation.confirmationMatches(payload.confirmation())) {
            finish(player, new DelvefoldAdminService.ServiceResult(ActionStatus.REJECTED,
                    payload.expectedRevision(), "Confirmation text did not match the requested operation.", false));
            return;
        }
        invoke(player, () -> DelvefoldAdminServices.get().perform(
                player,
                payload.expectedRevision(),
                operation,
                payload.confirmation()));
    }

    private static void invoke(ServerPlayer player, ServiceCall call) {
        try {
            DelvefoldAdminService.ServiceResult result = Objects.requireNonNull(call.run(), "service result");
            finish(player, result);
        } catch (RuntimeException exception) {
            LOGGER.error("Delvefold administration request failed for {}", player.getGameProfile().getName(), exception);
            finish(player, new DelvefoldAdminService.ServiceResult(ActionStatus.ERROR, 0L,
                    "The server rejected the request due to an internal error. See the server log.", true));
        }
    }

    private static void finish(ServerPlayer player, DelvefoldAdminService.ServiceResult result) {
        PacketDistributor.sendToPlayer(player, ActionResultPayload.from(result));
        if (result.refreshSnapshot() || result.status() == ActionStatus.STALE) {
            sendSnapshot(player, 0);
        }
    }

    private static void sendSnapshot(ServerPlayer player, int orePage) {
        try {
            AdminSnapshot snapshot = Objects.requireNonNull(DelvefoldAdminServices.get().snapshot(
                    player, orePage, ProtocolLimits.GUI_ORE_RULES_PER_PAGE), "snapshot");
            PacketDistributor.sendToPlayer(player, new OpenGuiPayload(snapshot));
        } catch (RuntimeException exception) {
            LOGGER.error("Could not create Delvefold GUI snapshot for {}", player.getGameProfile().getName(), exception);
            PacketDistributor.sendToPlayer(player, new ActionResultPayload(ActionStatus.ERROR, 0L,
                    "The server could not create the Delvefold administration snapshot."));
        }
    }

    private static ServerPlayer authorizedPlayer(IPayloadContext context, int permissionLevel) {
        ServerPlayer player = serverPlayer(context);
        return player != null && requirePermission(player, permissionLevel) ? player : null;
    }

    private static ServerPlayer serverPlayer(IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            return player;
        }
        LOGGER.warn("Ignored Delvefold serverbound payload without a ServerPlayer context");
        return null;
    }

    private static boolean requirePermission(ServerPlayer player, int permissionLevel) {
        boolean allowed = permissionLevel >= AdminAccess.WORLD_MANAGEMENT_PERMISSION
                ? AdminAccess.canManageWorld(player)
                : AdminAccess.canConfigure(player);
        if (allowed) {
            return true;
        }
        player.sendSystemMessage(Component.translatable("message.delvefold.permission_denied", permissionLevel));
        return false;
    }

    @FunctionalInterface
    private interface ServiceCall {
        DelvefoldAdminService.ServiceResult run();
    }
}
