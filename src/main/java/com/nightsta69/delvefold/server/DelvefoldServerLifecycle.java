package com.nightsta69.delvefold.server;

import com.mojang.logging.LogUtils;
import com.nightsta69.delvefold.config.DelvefoldConfigService;
import com.nightsta69.delvefold.config.importer.MinecraftOreImportRegistry;
import com.nightsta69.delvefold.config.importer.OreImportSessionService;
import com.nightsta69.delvefold.reset.WorldOperationService;
import com.nightsta69.delvefold.reset.WorldRestoreService;
import com.nightsta69.delvefold.reset.RenewalScheduler;
import java.io.IOException;
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

    private DelvefoldServerLifecycle() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        IEventBus gameBus = NeoForge.EVENT_BUS;
        gameBus.addListener(EventPriority.HIGHEST, ServerAboutToStartEvent.class,
                event -> {
                    MinecraftOreImportRegistry.invalidateCache();
                    OreImportSessionService.get().invalidateAll();
                    WorldRestoreService.get().prepareStartup(event.getServer());
                    WorldOperationService.get().prepareStartup(event.getServer());
                });
        gameBus.addListener(EventPriority.NORMAL, ServerAboutToStartEvent.class,
                DelvefoldServerLifecycle::loadConfiguration);
        gameBus.addListener(EventPriority.LOWEST, ServerAboutToStartEvent.class,
                event -> WorldOperationService.get().finishStartup(event.getServer()));
        gameBus.addListener(ServerTickEvent.Post.class, RenewalScheduler::onServerTick);
        gameBus.addListener(PlayerEvent.PlayerLoggedOutEvent.class,
                event -> OreImportSessionService.get().invalidatePlayer(event.getEntity().getUUID()));
        gameBus.addListener(ServerStoppingEvent.class, event -> {
            WorldOperationService.get().stop(event.getServer());
            WorldRestoreService.get().stop();
            RenewalScheduler.reset();
            MinecraftOreImportRegistry.invalidateCache();
            OreImportSessionService.get().invalidateAll();
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
}
