package com.nightsta69.delvefold.network;

import com.mojang.logging.LogUtils;
import com.nightsta69.delvefold.admin.AdminLocalizedMessage;
import com.nightsta69.delvefold.admin.OreImportAdminService;
import com.nightsta69.delvefold.admin.OreLibraryAdminService;
import com.nightsta69.delvefold.audit.DelvefoldAuditService;
import com.nightsta69.delvefold.config.AdminAccess;
import com.nightsta69.delvefold.network.model.ActionStatus;
import com.nightsta69.delvefold.network.model.AdminOperation;
import com.nightsta69.delvefold.network.model.AdminSnapshot;
import com.nightsta69.delvefold.network.payload.ActionResultPayload;
import com.nightsta69.delvefold.network.payload.AddOreFamiliesPayload;
import com.nightsta69.delvefold.network.payload.AdminActionPayload;
import com.nightsta69.delvefold.network.payload.BackupActionPayload;
import com.nightsta69.delvefold.network.payload.DeleteOreRulePayload;
import com.nightsta69.delvefold.network.payload.ForecastRequestPayload;
import com.nightsta69.delvefold.network.payload.GameplayUpdatePayload;
import com.nightsta69.delvefold.network.payload.GuideOpenedPayload;
import com.nightsta69.delvefold.network.payload.IdentityUpdatePayload;
import com.nightsta69.delvefold.network.payload.InitializeWorldPayload;
import com.nightsta69.delvefold.network.payload.OpenForecastPayload;
import com.nightsta69.delvefold.network.payload.OpenGuiPayload;
import com.nightsta69.delvefold.network.payload.OpenGuiRequestPayload;
import com.nightsta69.delvefold.network.payload.OpenGuidePayload;
import com.nightsta69.delvefold.network.payload.OpenOreImportPreviewPayload;
import com.nightsta69.delvefold.network.payload.OpenOreImportScanPayload;
import com.nightsta69.delvefold.network.payload.OpenOreLibraryPayload;
import com.nightsta69.delvefold.network.payload.OreImportCreatePayload;
import com.nightsta69.delvefold.network.payload.OreImportPreviewPageRequestPayload;
import com.nightsta69.delvefold.network.payload.OreImportPreviewRequestPayload;
import com.nightsta69.delvefold.network.payload.OreImportScanPageRequestPayload;
import com.nightsta69.delvefold.network.payload.OreImportScanRequestPayload;
import com.nightsta69.delvefold.network.payload.OreLibraryRequestPayload;
import com.nightsta69.delvefold.network.payload.OrePageRequestPayload;
import com.nightsta69.delvefold.network.payload.PortalUpdatePayload;
import com.nightsta69.delvefold.network.payload.ProfileActionPayload;
import com.nightsta69.delvefold.network.payload.ProfileExportPayload;
import com.nightsta69.delvefold.network.payload.ProfileExportRequestPayload;
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
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/** Common payload registration and server-authoritative request handlers. */
public final class DelvefoldNetwork {
    /** Exact client/server payload compatibility version; protocol 14 field order and enum ordinals are immutable. */
    public static final String PROTOCOL_VERSION = "14";

    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile Consumer<OpenGuiPayload> clientOpenHandler = payload -> {};
    private static volatile Consumer<ActionResultPayload> clientResultHandler = payload -> {};
    private static volatile Consumer<ProfileExportPayload> clientProfileExportHandler = payload -> {};
    private static volatile Consumer<OpenGuidePayload> clientGuideHandler = payload -> {};
    private static volatile Consumer<OpenForecastPayload> clientForecastHandler = payload -> {};
    private static volatile Consumer<OpenOreImportScanPayload> clientImportScanHandler = payload -> {};
    private static volatile Consumer<OpenOreImportPreviewPayload> clientImportPreviewHandler = payload -> {};
    private static volatile Consumer<OpenOreLibraryPayload> clientOreLibraryHandler = payload -> {};

    private DelvefoldNetwork() {}

    /**
     * Registers common payload declarations during mod construction.
     *
     * @param modEventBus Delvefold mod lifecycle event bus
     */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(DelvefoldNetwork::registerPayloadHandlers);
    }

    /**
     * Installs client-thread terminal handlers without introducing client-only types into this dedicated-server-safe
     * class.
     *
     * @param openHandler administration snapshot screen handler
     * @param resultHandler administration result handler
     * @param profileExportHandler clipboard-safe profile export handler
     * @param guideHandler public guide screen handler
     * @param forecastHandler administrative forecast screen handler
     * @param importScanHandler ore-import scan page handler
     * @param importPreviewHandler ore-import preview page handler
     * @param oreLibraryHandler Unified Ores library page handler
     */
    public static void installClientHandlers(
            Consumer<OpenGuiPayload> openHandler,
            Consumer<ActionResultPayload> resultHandler,
            Consumer<ProfileExportPayload> profileExportHandler,
            Consumer<OpenGuidePayload> guideHandler,
            Consumer<OpenForecastPayload> forecastHandler,
            Consumer<OpenOreImportScanPayload> importScanHandler,
            Consumer<OpenOreImportPreviewPayload> importPreviewHandler,
            Consumer<OpenOreLibraryPayload> oreLibraryHandler) {
        clientOpenHandler = Objects.requireNonNull(openHandler, "openHandler");
        clientResultHandler = Objects.requireNonNull(resultHandler, "resultHandler");
        clientProfileExportHandler = Objects.requireNonNull(profileExportHandler, "profileExportHandler");
        clientGuideHandler = Objects.requireNonNull(guideHandler, "guideHandler");
        clientForecastHandler = Objects.requireNonNull(forecastHandler, "forecastHandler");
        clientImportScanHandler = Objects.requireNonNull(importScanHandler, "importScanHandler");
        clientImportPreviewHandler = Objects.requireNonNull(importPreviewHandler, "importPreviewHandler");
        clientOreLibraryHandler = Objects.requireNonNull(oreLibraryHandler, "oreLibraryHandler");
    }

    /**
     * Opens the administration GUI for an authorized server player.
     *
     * @param player requesting server player
     * @return {@code true} when configuration permission was granted and a snapshot was sent
     */
    public static boolean openFor(ServerPlayer player) {
        if (!requirePermission(player, 2)) {
            return false;
        }
        sendSnapshot(player, 0);
        return true;
    }

    private static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(
                OpenGuiRequestPayload.TYPE, OpenGuiRequestPayload.STREAM_CODEC, DelvefoldNetwork::handleOpenRequest);
        registrar.playToServer(
                OrePageRequestPayload.TYPE, OrePageRequestPayload.STREAM_CODEC, DelvefoldNetwork::handleOrePageRequest);
        registrar.playToServer(
                InitializeWorldPayload.TYPE, InitializeWorldPayload.STREAM_CODEC, DelvefoldNetwork::handleInitialize);
        registrar.playToServer(
                IdentityUpdatePayload.TYPE, IdentityUpdatePayload.STREAM_CODEC, DelvefoldNetwork::handleIdentityUpdate);
        registrar.playToServer(
                SaveOreRulePayload.TYPE, SaveOreRulePayload.STREAM_CODEC, DelvefoldNetwork::handleSaveOreRule);
        registrar.playToServer(
                DeleteOreRulePayload.TYPE, DeleteOreRulePayload.STREAM_CODEC, DelvefoldNetwork::handleDeleteOreRule);
        registrar.playToServer(
                GameplayUpdatePayload.TYPE, GameplayUpdatePayload.STREAM_CODEC, DelvefoldNetwork::handleGameplayUpdate);
        registrar.playToServer(
                PortalUpdatePayload.TYPE, PortalUpdatePayload.STREAM_CODEC, DelvefoldNetwork::handlePortalUpdate);
        registrar.playToServer(
                ProfileActionPayload.TYPE, ProfileActionPayload.STREAM_CODEC, DelvefoldNetwork::handleProfileAction);
        registrar.playToServer(
                ProfileExportRequestPayload.TYPE,
                ProfileExportRequestPayload.STREAM_CODEC,
                DelvefoldNetwork::handleProfileExportRequest);
        registrar.playToServer(
                BackupActionPayload.TYPE, BackupActionPayload.STREAM_CODEC, DelvefoldNetwork::handleBackupAction);
        registrar.playToServer(
                AdminActionPayload.TYPE, AdminActionPayload.STREAM_CODEC, DelvefoldNetwork::handleAdminAction);
        registrar.playToServer(
                GuideOpenedPayload.TYPE, GuideOpenedPayload.STREAM_CODEC, DelvefoldNetwork::handleGuideOpened);
        registrar.playToServer(
                ForecastRequestPayload.TYPE,
                ForecastRequestPayload.STREAM_CODEC,
                DelvefoldNetwork::handleForecastRequest);
        registrar.playToServer(
                OreImportScanRequestPayload.TYPE,
                OreImportScanRequestPayload.STREAM_CODEC,
                DelvefoldNetwork::handleImportScan);
        registrar.playToServer(
                OreImportScanPageRequestPayload.TYPE,
                OreImportScanPageRequestPayload.STREAM_CODEC,
                DelvefoldNetwork::handleImportScanPage);
        registrar.playToServer(
                OreImportPreviewRequestPayload.TYPE,
                OreImportPreviewRequestPayload.STREAM_CODEC,
                DelvefoldNetwork::handleImportPreview);
        registrar.playToServer(
                OreImportPreviewPageRequestPayload.TYPE,
                OreImportPreviewPageRequestPayload.STREAM_CODEC,
                DelvefoldNetwork::handleImportPreviewPage);
        registrar.playToServer(
                OreImportCreatePayload.TYPE, OreImportCreatePayload.STREAM_CODEC, DelvefoldNetwork::handleImportCreate);
        registrar.playToServer(
                OreLibraryRequestPayload.TYPE,
                OreLibraryRequestPayload.STREAM_CODEC,
                DelvefoldNetwork::handleOreLibraryRequest);
        registrar.playToServer(
                AddOreFamiliesPayload.TYPE, AddOreFamiliesPayload.STREAM_CODEC, DelvefoldNetwork::handleAddOreFamilies);

        registrar.playToClient(
                OpenGuiPayload.TYPE,
                OpenGuiPayload.STREAM_CODEC,
                (payload, context) -> clientOpenHandler.accept(payload));
        registrar.playToClient(
                ActionResultPayload.TYPE,
                ActionResultPayload.STREAM_CODEC,
                (payload, context) -> clientResultHandler.accept(payload));
        registrar.playToClient(
                ProfileExportPayload.TYPE,
                ProfileExportPayload.STREAM_CODEC,
                (payload, context) -> clientProfileExportHandler.accept(payload));
        registrar.playToClient(
                OpenGuidePayload.TYPE,
                OpenGuidePayload.STREAM_CODEC,
                (payload, context) -> clientGuideHandler.accept(payload));
        registrar.playToClient(
                OpenForecastPayload.TYPE,
                OpenForecastPayload.STREAM_CODEC,
                (payload, context) -> clientForecastHandler.accept(payload));
        registrar.playToClient(
                OpenOreImportScanPayload.TYPE,
                OpenOreImportScanPayload.STREAM_CODEC,
                (payload, context) -> clientImportScanHandler.accept(payload));
        registrar.playToClient(
                OpenOreImportPreviewPayload.TYPE,
                OpenOreImportPreviewPayload.STREAM_CODEC,
                (payload, context) -> clientImportPreviewHandler.accept(payload));
        registrar.playToClient(
                OpenOreLibraryPayload.TYPE,
                OpenOreLibraryPayload.STREAM_CODEC,
                (payload, context) -> clientOreLibraryHandler.accept(payload));
    }

    private static void handleOpenRequest(OpenGuiRequestPayload payload, IPayloadContext context) {
        ServerPlayer player = serverPlayer(context);
        if (player != null) {
            openFor(player);
        }
    }

    private static void handleGuideOpened(GuideOpenedPayload payload, IPayloadContext context) {
        ServerPlayer player = serverPlayer(context);
        if (player != null) {
            com.nightsta69.delvefold.guide.DelvefoldGuideService.confirmOpened(player, payload.authorizationId());
        }
    }

    private static void handleForecastRequest(ForecastRequestPayload payload, IPayloadContext context) {
        ServerPlayer player = authorizedPlayer(context, AdminAccess.CONFIGURE_PERMISSION);
        if (player == null) {
            return;
        }
        try {
            var forecast = DelvefoldAdminServices.get().forecast(player, payload.profileId(), payload.page());
            PacketDistributor.sendToPlayer(player, new OpenForecastPayload(forecast));
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Could not build Delvefold ore forecast for {}",
                    player.getGameProfile().getName(),
                    exception);
            finish(
                    player,
                    new DelvefoldAdminService.ServiceResult(
                            ActionStatus.REJECTED,
                            com.nightsta69.delvefold.config.DelvefoldConfigService.get()
                                    .snapshot()
                                    .ores()
                                    .revision(),
                            AdminLocalizedMessage.encode(
                                    "message.delvefold.network.forecast_failed", exception.getMessage()),
                            false));
        }
    }

    private static void handleImportScan(OreImportScanRequestPayload payload, IPayloadContext context) {
        ServerPlayer player = authorizedPlayer(context, AdminAccess.CONFIGURE_PERMISSION);
        if (player == null) return;
        var result = OreImportAdminService.get().scan(player, payload.expectedOreRevision(), payload.includeVanilla());
        if (result.accepted()) {
            PacketDistributor.sendToPlayer(player, new OpenOreImportScanPayload(acceptedImportView(result)));
        } else {
            finishImportFailure(player, result.status(), result.message());
        }
    }

    private static void handleImportScanPage(OreImportScanPageRequestPayload payload, IPayloadContext context) {
        ServerPlayer player = authorizedPlayer(context, AdminAccess.CONFIGURE_PERMISSION);
        if (player == null) return;
        var result = OreImportAdminService.get().scanPage(player, payload.scanToken(), payload.page());
        if (result.accepted()) {
            PacketDistributor.sendToPlayer(player, new OpenOreImportScanPayload(acceptedImportView(result)));
        } else {
            finishImportFailure(player, result.status(), result.message());
        }
    }

    private static void handleImportPreview(OreImportPreviewRequestPayload payload, IPayloadContext context) {
        ServerPlayer player = authorizedPlayer(context, AdminAccess.CONFIGURE_PERMISSION);
        if (player == null) return;
        var result = OreImportAdminService.get().preview(player, payload.scanToken(), payload.selectedGroupIds());
        if (result.accepted()) {
            PacketDistributor.sendToPlayer(player, new OpenOreImportPreviewPayload(acceptedImportView(result)));
        } else {
            finishImportFailure(player, result.status(), result.message());
        }
    }

    private static void handleImportPreviewPage(OreImportPreviewPageRequestPayload payload, IPayloadContext context) {
        ServerPlayer player = authorizedPlayer(context, AdminAccess.CONFIGURE_PERMISSION);
        if (player == null) return;
        var result = OreImportAdminService.get().previewPage(player, payload.commitToken(), payload.page());
        if (result.accepted()) {
            PacketDistributor.sendToPlayer(player, new OpenOreImportPreviewPayload(acceptedImportView(result)));
        } else {
            finishImportFailure(player, result.status(), result.message());
        }
    }

    private static void handleImportCreate(OreImportCreatePayload payload, IPayloadContext context) {
        ServerPlayer player = authorizedPlayer(context, AdminAccess.CONFIGURE_PERMISSION);
        if (player != null) {
            invoke(
                    player,
                    () -> OreImportAdminService.get().create(player, payload.commitToken(), payload.targetProfileId()));
        }
    }

    private static void handleOreLibraryRequest(OreLibraryRequestPayload payload, IPayloadContext context) {
        ServerPlayer player = authorizedPlayer(context, AdminAccess.CONFIGURE_PERMISSION);
        if (player == null) return;
        var result = OreLibraryAdminService.get().page(player, payload);
        if (result.accepted()) {
            PacketDistributor.sendToPlayer(player, new OpenOreLibraryPayload(acceptedImportView(result)));
        } else {
            finishImportFailure(player, result.status(), result.message());
        }
    }

    private static void handleAddOreFamilies(AddOreFamiliesPayload payload, IPayloadContext context) {
        ServerPlayer player = authorizedPlayer(context, AdminAccess.CONFIGURE_PERMISSION);
        if (player != null) {
            invoke(player, () -> OreLibraryAdminService.get().addFamilies(player, payload));
        }
    }

    private static void finishImportFailure(ServerPlayer player, ActionStatus status, String message) {
        long revision;
        try {
            revision = com.nightsta69.delvefold.config.DelvefoldConfigService.get()
                    .snapshot()
                    .ores()
                    .revision();
        } catch (IllegalStateException ignored) {
            revision = 0L;
        }
        finish(
                player,
                new DelvefoldAdminService.ServiceResult(status, revision, message, status == ActionStatus.STALE));
    }

    private static <T> T acceptedImportView(OreImportAdminService.ViewResult<T> result) {
        return Objects.requireNonNull(result.view(), "accepted import result view");
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
            finish(
                    player,
                    new DelvefoldAdminService.ServiceResult(
                            ActionStatus.REJECTED,
                            payload.expectedSettingsRevision(),
                            AdminLocalizedMessage.encode("message.delvefold.network.initialize_confirmation"),
                            false));
            return;
        }
        invoke(
                player,
                () -> DelvefoldAdminServices.get()
                        .initialize(
                                player,
                                payload.expectedOreRevision(),
                                payload.expectedSettingsRevision(),
                                payload.terrainMode(),
                                payload.orePreset(),
                                payload.gameplay(),
                                payload.identity()));
    }

    private static void handleSaveOreRule(SaveOreRulePayload payload, IPayloadContext context) {
        ServerPlayer player = authorizedPlayer(context, 2);
        if (player != null) {
            invoke(player, () -> {
                DelvefoldAdminService.ServiceResult result = DelvefoldAdminServices.get()
                        .saveOreRule(player, payload.expectedRevision(), payload.rule(), payload.createOnly());
                if (result.status() == ActionStatus.ACCEPTED) {
                    com.nightsta69.delvefold.config.importer.OreImportSessionService.get()
                            .invalidatePlayer(player.getUUID());
                }
                return result;
            });
        }
    }

    private static void handleIdentityUpdate(IdentityUpdatePayload payload, IPayloadContext context) {
        ServerPlayer player = authorizedPlayer(context, 2);
        if (player != null) {
            invoke(
                    player,
                    () -> DelvefoldAdminServices.get()
                            .updateIdentity(player, payload.expectedRevision(), payload.identity()));
        }
    }

    private static void handleDeleteOreRule(DeleteOreRulePayload payload, IPayloadContext context) {
        ServerPlayer player = authorizedPlayer(context, 2);
        if (player != null) {
            invoke(
                    player,
                    () -> DelvefoldAdminServices.get()
                            .deleteOreRule(player, payload.expectedRevision(), payload.ruleId()));
        }
    }

    private static void handleGameplayUpdate(GameplayUpdatePayload payload, IPayloadContext context) {
        ServerPlayer player = authorizedPlayer(context, 2);
        if (player != null) {
            invoke(
                    player,
                    () -> DelvefoldAdminServices.get()
                            .updateGameplay(player, payload.expectedRevision(), payload.gameplay()));
        }
    }

    private static void handlePortalUpdate(PortalUpdatePayload payload, IPayloadContext context) {
        ServerPlayer player = authorizedPlayer(context, 2);
        if (player != null) {
            invoke(
                    player,
                    () -> DelvefoldAdminServices.get()
                            .updatePortal(player, payload.expectedRevision(), payload.portal()));
        }
    }

    private static void handleProfileAction(ProfileActionPayload payload, IPayloadContext context) {
        ServerPlayer player = authorizedPlayer(context, 2);
        if (player != null) {
            invoke(
                    player,
                    () -> DelvefoldAdminServices.get()
                            .performProfile(
                                    player,
                                    payload.expectedOreRevision(),
                                    payload.operation(),
                                    payload.sourceId(),
                                    payload.targetId(),
                                    payload.json(),
                                    payload.overwrite()));
        }
    }

    private static void handleProfileExportRequest(ProfileExportRequestPayload payload, IPayloadContext context) {
        ServerPlayer player = authorizedPlayer(context, 2);
        if (player == null) {
            return;
        }
        try {
            String json =
                    com.nightsta69.delvefold.config.DelvefoldConfigService.get().exportProfileJson(payload.profileId());
            if (json.length() > ProtocolLimits.MAX_PROFILE_CLIPBOARD_CHARS) {
                finish(
                        player,
                        new DelvefoldAdminService.ServiceResult(
                                ActionStatus.REJECTED,
                                com.nightsta69.delvefold.config.DelvefoldConfigService.get()
                                        .snapshot()
                                        .ores()
                                        .revision(),
                                AdminLocalizedMessage.encode("message.delvefold.network.profile_clipboard_large"),
                                false));
                return;
            }
            PacketDistributor.sendToPlayer(player, new ProfileExportPayload(payload.profileId(), json));
        } catch (IOException | IllegalArgumentException exception) {
            finish(
                    player,
                    new DelvefoldAdminService.ServiceResult(
                            ActionStatus.ERROR,
                            0,
                            AdminLocalizedMessage.encode(
                                    "message.delvefold.network.profile_export_failed", exception.getMessage()),
                            false));
        }
    }

    private static void handleBackupAction(BackupActionPayload payload, IPayloadContext context) {
        ServerPlayer player = authorizedPlayer(context, AdminAccess.WORLD_MANAGEMENT_PERMISSION);
        if (player != null) {
            invoke(
                    player,
                    () -> DelvefoldAdminServices.get()
                            .performBackup(
                                    player,
                                    payload.expectedSettingsRevision(),
                                    payload.operation(),
                                    payload.backupId()));
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
            finish(
                    player,
                    new DelvefoldAdminService.ServiceResult(
                            ActionStatus.REJECTED,
                            payload.expectedRevision(),
                            AdminLocalizedMessage.encode("message.delvefold.network.confirmation_mismatch"),
                            false));
            return;
        }
        invoke(
                player,
                () -> DelvefoldAdminServices.get()
                        .perform(player, payload.expectedRevision(), operation, payload.confirmation()));
    }

    // Closing the intentionally unused scope restores the thread-local audit actor.
    @SuppressWarnings("try")
    private static void invoke(ServerPlayer player, ServiceCall call) {
        try (DelvefoldAuditService.ActorScope ignored =
                DelvefoldAuditService.get().pushActor(player.getGameProfile().getName())) {
            DelvefoldAdminService.ServiceResult result = Objects.requireNonNull(call.run(), "service result");
            finish(player, result);
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Delvefold administration request failed for {}",
                    player.getGameProfile().getName(),
                    exception);
            finish(
                    player,
                    new DelvefoldAdminService.ServiceResult(
                            ActionStatus.ERROR,
                            0L,
                            AdminLocalizedMessage.encode("message.delvefold.network.internal_error"),
                            true));
        }
    }

    private static void finish(ServerPlayer player, DelvefoldAdminService.ServiceResult result) {
        PacketDistributor.sendToPlayer(player, ActionResultPayload.from(result));
        if (result.refreshSnapshot() || result.status() == ActionStatus.STALE) {
            sendSnapshot(player, 0);
        }
    }

    /**
     * Completes a previously accepted background administration action on the server thread.
     *
     * <p>This method sends immediately and does not perform a thread handoff; asynchronous callers must first re-enter
     * the server game thread, normally with {@code MinecraftServer.execute(...)}.
     *
     * @param player player that initiated the asynchronous action
     * @param result immutable terminal result, including whether a refreshed snapshot is required
     */
    public static void sendAsyncResult(ServerPlayer player, DelvefoldAdminService.ServiceResult result) {
        finish(Objects.requireNonNull(player, "player"), Objects.requireNonNull(result, "result"));
    }

    private static void sendSnapshot(ServerPlayer player, int orePage) {
        try {
            AdminSnapshot snapshot = Objects.requireNonNull(
                    DelvefoldAdminServices.get().snapshot(player, orePage, ProtocolLimits.GUI_ORE_RULES_PER_PAGE),
                    "snapshot");
            PacketDistributor.sendToPlayer(player, new OpenGuiPayload(snapshot));
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Could not create Delvefold GUI snapshot for {}",
                    player.getGameProfile().getName(),
                    exception);
            PacketDistributor.sendToPlayer(
                    player,
                    new ActionResultPayload(
                            ActionStatus.ERROR,
                            0L,
                            AdminLocalizedMessage.encode("message.delvefold.network.snapshot_failed")));
        }
    }

    private static @Nullable ServerPlayer authorizedPlayer(IPayloadContext context, int permissionLevel) {
        ServerPlayer player = serverPlayer(context);
        return player != null && requirePermission(player, permissionLevel) ? player : null;
    }

    private static @Nullable ServerPlayer serverPlayer(IPayloadContext context) {
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
