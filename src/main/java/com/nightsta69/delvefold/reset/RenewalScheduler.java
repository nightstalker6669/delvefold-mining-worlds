package com.nightsta69.delvefold.reset;

import com.mojang.logging.LogUtils;
import com.nightsta69.delvefold.audit.AuditMutation;
import com.nightsta69.delvefold.audit.DelvefoldAuditService;
import com.nightsta69.delvefold.config.ConfigSnapshot;
import com.nightsta69.delvefold.config.ConfigWriteResult;
import com.nightsta69.delvefold.config.DelvefoldConfigService;
import com.nightsta69.delvefold.config.model.RenewalSettings;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/** Announces and safely stages opt-in world renewal; filesystem work still waits for restart. */
public final class RenewalScheduler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long CHECK_INTERVAL_TICKS = 20L * 20L;
    private static final Set<String> ANNOUNCED = new HashSet<>();
    private static long lastCheckTick;

    private RenewalScheduler() {
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long tick = server.getTickCount();
        if (tick - lastCheckTick < CHECK_INTERVAL_TICKS && tick >= lastCheckTick) {
            return;
        }
        lastCheckTick = tick;
        check(server, System.currentTimeMillis());
    }

    static void check(MinecraftServer server, long now) {
        ConfigSnapshot snapshot;
        try {
            snapshot = DelvefoldConfigService.get().snapshot();
        } catch (IllegalStateException ignored) {
            return;
        }
        RenewalSettings renewal = snapshot.settings().identity().renewal();
        if (!snapshot.settings().initialized() || !renewal.enabled()) {
            ANNOUNCED.clear();
            return;
        }
        if (renewal.nextRenewalAtEpochMillis() == 0L) {
            ConfigWriteResult scheduled = DelvefoldConfigService.get().updateSettings(
                    snapshot.settings().revision(),
                    settings -> settings.withIdentity(settings.identity().withRenewal(renewal.scheduledFrom(now))));
            if (!scheduled.saved()) {
                LOGGER.warn("Could not establish Delvefold renewal schedule: {}", scheduled.issues());
            }
            return;
        }
        long remainingMillis = renewal.nextRenewalAtEpochMillis() - now;
        if (remainingMillis <= 0L) {
            stageRenewal(server, snapshot, renewal, now);
            return;
        }
        for (int minutes : RenewalSettings.warningThresholds(renewal.warningMinutes())) {
            if (remainingMillis <= minutes * 60_000L) {
                String key = renewal.nextRenewalAtEpochMillis() + ":" + minutes;
                if (ANNOUNCED.add(key)) {
                    broadcast(server, Component.translatable(minutes == 1
                                    ? "message.delvefold.renewal.warning_one"
                                    : "message.delvefold.renewal.warning_many",
                            minutes));
                }
            }
        }
    }

    private static void stageRenewal(
            MinecraftServer server, ConfigSnapshot snapshot, RenewalSettings renewal, long now) {
        if (WorldOperationService.get().isEntryBlocked() || WorldOperationService.get().hasPending(server)) {
            return;
        }
        WorldOperationPreview preview = WorldOperationService.get().request(server,
                WorldOperationRequest.recreate(
                        snapshot.settings().terrainMode(), snapshot.settings().identity().terrainVariant()),
                "scheduled-renewal");
        if (!preview.accepted()) {
            LOGGER.error("Scheduled Delvefold renewal was rejected: {}", preview.message());
            return;
        }
        WorldOperationResult result = WorldOperationService.get().confirm(server, preview.confirmationToken());
        if (result.success()) {
            auditWorldOperation(AuditMutation.Operation.WORLD_OPERATION_ACCEPTED,
                    snapshot.settings().revision());
            ConfigWriteResult rescheduled = DelvefoldConfigService.get().updateSettings(
                    snapshot.settings().revision(),
                    settings -> settings.withIdentity(settings.identity().withRenewal(renewal.scheduledFrom(now))));
            if (!rescheduled.saved()) {
                WorldOperationResult cancelled = WorldOperationService.get().cancelConfirmed(server);
                if (cancelled.success()) {
                    auditWorldOperation(AuditMutation.Operation.WORLD_OPERATION_CANCELLED,
                            snapshot.settings().revision());
                }
                LOGGER.error("Scheduled renewal was cancelled because its next-run time could not be saved: {} ({})",
                        rescheduled.issues(), cancelled.message());
                return;
            }
            ANNOUNCED.clear();
            broadcast(server, Component.translatable("message.delvefold.renewal.ready"));
        } else {
            LOGGER.error("Scheduled Delvefold renewal could not be confirmed: {}", result.message());
        }
    }

    private static void auditWorldOperation(AuditMutation.Operation operation, long settingsRevision) {
        DelvefoldAuditService.get().record(new AuditMutation(
                "server",
                operation,
                AuditMutation.ObjectType.WORLD,
                "mining_world",
                settingsRevision,
                settingsRevision));
    }

    public static void reset() {
        ANNOUNCED.clear();
        lastCheckTick = 0L;
    }

    private static void broadcast(MinecraftServer server, Component message) {
        server.getPlayerList().broadcastSystemMessage(message, false);
        LOGGER.info(message.getString());
    }
}
