package com.nightsta69.delvefold.server;

import com.mojang.logging.LogUtils;
import com.nightsta69.delvefold.audit.AsyncAuditMutationTracker;
import com.nightsta69.delvefold.audit.AuditMutation;
import com.nightsta69.delvefold.audit.DelvefoldAuditService;
import com.nightsta69.delvefold.config.DelvefoldConfigService;
import com.nightsta69.delvefold.config.importer.MinecraftOreImportRegistry;
import com.nightsta69.delvefold.config.importer.OreImportSessionService;
import com.nightsta69.delvefold.diagnostics.DelvefoldDoctorService;
import com.nightsta69.delvefold.reset.BackupCatalogCache;
import com.nightsta69.delvefold.reset.BackupDeletionGuard;
import com.nightsta69.delvefold.reset.BackupRetentionRunState;
import com.nightsta69.delvefold.reset.BackupRetentionService;
import com.nightsta69.delvefold.reset.RenewalScheduler;
import com.nightsta69.delvefold.reset.WorldOperationService;
import com.nightsta69.delvefold.reset.WorldRestoreService;
import com.nightsta69.delvefold.world.DelvefoldWorldgen;
import com.nightsta69.delvefold.world.landmark.catalog.LandmarkCatalogReloadListener;
import com.nightsta69.delvefold.world.landmark.catalog.LandmarkCatalogService;
import java.io.IOException;
import java.time.Instant;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/** Orders startup so pending dimension moves precede config publication. */
public final class DelvefoldServerLifecycle {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean registered;

    private DelvefoldServerLifecycle() {}

    /**
     * Registers Delvefold's ordered server lifecycle listeners exactly once per process.
     *
     * <p>Startup recovery runs before configuration publication; shutdown stops admission, drains accepted audit work,
     * and then clears server-bound caches and services.
     */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        IEventBus gameBus = NeoForge.EVENT_BUS;
        gameBus.addListener(EventPriority.HIGHEST, ServerAboutToStartEvent.class, event -> {
            var saveRoot = event.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
            AsyncAuditMutationTracker.get().beginSession(saveRoot);
            DelvefoldAuditService.get().start(event.getServer());
            AsyncAuditMutationTracker.get().openSession(saveRoot);
            LandmarkCatalogReloadListener.flushPendingAudit();
            DelvefoldWorldgen.MINING_ORE_FEATURE.get().invalidateRuntimeProfile();
            MinecraftOreImportRegistry.invalidateCache();
            OreImportSessionService.get().invalidateAll();
            WorldRestoreService.get().prepareStartup(event.getServer());
            WorldOperationService.get().prepareStartup(event.getServer());
        });
        gameBus.addListener(
                EventPriority.NORMAL, ServerAboutToStartEvent.class, DelvefoldServerLifecycle::loadConfiguration);
        gameBus.addListener(EventPriority.LOWEST, ServerAboutToStartEvent.class, event -> {
            WorldOperationService.get().finishStartup(event.getServer());
            applyBackupRetention(event);
        });
        gameBus.addListener(ServerTickEvent.Post.class, RenewalScheduler::onServerTick);
        gameBus.addListener(
                PlayerEvent.PlayerLoggedOutEvent.class,
                event -> OreImportSessionService.get()
                        .invalidatePlayer(event.getEntity().getUUID()));
        gameBus.addListener(ServerStoppingEvent.class, event -> {
            var saveRoot = event.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
            AsyncAuditMutationTracker asyncAudits = AsyncAuditMutationTracker.get();
            asyncAudits.stopAccepting(saveRoot);
            BackupDeletionGuard.get().clear(event.getServer());
            asyncAudits.drain(saveRoot);
            WorldOperationService.get().stop(event.getServer());
            WorldRestoreService.get().stop();
            RenewalScheduler.reset();
            BackupRetentionRunState.get().reset();
            BackupCatalogCache.get().clear();
            DelvefoldDoctorService.get().clear();
            DelvefoldWorldgen.MINING_ORE_FEATURE.get().invalidateRuntimeProfile();
            MinecraftOreImportRegistry.invalidateCache();
            OreImportSessionService.get().invalidateAll();
            LandmarkCatalogService.get().reset();
            DelvefoldAuditService.get().stop(event.getServer());
            asyncAudits.endSession(saveRoot);
            DelvefoldConfigService.get().stop(event.getServer());
        });
    }

    private static void loadConfiguration(ServerAboutToStartEvent event) {
        try {
            DelvefoldConfigService.get().start(event.getServer());
        } catch (IOException exception) {
            LOGGER.error("Delvefold could not load its per-save configuration", exception);
            throw new IllegalStateException("Delvefold configuration startup failed", exception);
        }
    }

    private static void applyBackupRetention(ServerAboutToStartEvent event) {
        var saveRoot = event.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        try {
            var server = event.getServer();
            var settings = DelvefoldConfigService.get().snapshot().settings().backupRetention();
            var preview = BackupRetentionService.preview(
                    server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT), settings, Instant.now());
            BackupRetentionRunState.get().recordPreview(preview);
            for (var prune : preview.plan().prunes()) {
                LOGGER.info(
                        "Delvefold backup retention preview: prune {} for {} ({} bytes)",
                        prune.id(),
                        prune.reasons(),
                        prune.sizeBytes());
                DelvefoldAuditService.get()
                        .record(new AuditMutation(
                                "server",
                                AuditMutation.Operation.RETENTION_PRUNE_PREVIEWED,
                                AuditMutation.ObjectType.BACKUP,
                                prune.id(),
                                -1L,
                                -1L));
            }
            var result = BackupRetentionService.apply(preview);
            BackupRetentionRunState.get().recordResult(result);
            for (String id : result.prunedIds()) {
                LOGGER.info("Delvefold backup retention pruned {}", id);
                DelvefoldAuditService.get()
                        .record(new AuditMutation(
                                "server",
                                AuditMutation.Operation.RETENTION_PRUNE_ACCEPTED,
                                AuditMutation.ObjectType.BACKUP,
                                id,
                                -1L,
                                -1L));
            }
            result.failures()
                    .forEach((id, failure) -> LOGGER.warn("Delvefold backup retention kept {}: {}", id, failure));
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Delvefold skipped automatic backup retention: {}", exception.getMessage());
        } finally {
            var unusedRefresh = BackupCatalogCache.get().refresh(saveRoot);
        }
    }
}
