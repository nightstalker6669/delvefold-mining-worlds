package com.nightsta69.delvefold;

import com.nightsta69.delvefold.admin.DefaultDelvefoldAdminService;
import com.nightsta69.delvefold.client.DelvefoldClientEvents;
import com.nightsta69.delvefold.command.DelvefoldCommands;
import com.nightsta69.delvefold.config.DelvefoldPermissions;
import com.nightsta69.delvefold.config.EcosystemProfileReloadListener;
import com.nightsta69.delvefold.gameplay.SpawnPolicy;
import com.nightsta69.delvefold.network.DelvefoldNetwork;
import com.nightsta69.delvefold.network.service.DelvefoldAdminServices;
import com.nightsta69.delvefold.portal.CentralHubProtectionEvents;
import com.nightsta69.delvefold.portal.PortalRegistries;
import com.nightsta69.delvefold.reset.MiningPlayerSafety;
import com.nightsta69.delvefold.server.DelvefoldServerLifecycle;
import com.nightsta69.delvefold.world.DelvefoldWorldgen;
import com.nightsta69.delvefold.world.GeologyAmbienceService;
import com.nightsta69.delvefold.world.landmark.LandmarkDiscoveryService;
import com.nightsta69.delvefold.world.landmark.LandmarkRegistries;
import com.nightsta69.delvefold.world.landmark.catalog.LandmarkCatalogReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/** NeoForge entry point that wires Delvefold registries, network handlers, reloaders, and lifecycle services. */
@Mod(Delvefold.MOD_ID)
public final class Delvefold {
    /** Stable namespace used by every Delvefold registry entry, resource, payload, and saved identifier. */
    public static final String MOD_ID = "delvefold";

    /**
     * Installs process-level integration during NeoForge mod construction.
     *
     * @param modEventBus Delvefold's mod lifecycle event bus
     * @param modContainer NeoForge container for this mod instance
     * @param dist physical distribution used to avoid loading client classes on dedicated servers
     */
    public Delvefold(IEventBus modEventBus, ModContainer modContainer, Dist dist) {
        DelvefoldWorldgen.register(modEventBus);
        LandmarkRegistries.register(modEventBus);
        PortalRegistries.register(modEventBus);
        CentralHubProtectionEvents.register();
        DelvefoldNetwork.register(modEventBus);
        if (dist == Dist.CLIENT) {
            DelvefoldClientEvents.register(modEventBus);
        }
        DelvefoldAdminServices.install(new DefaultDelvefoldAdminService());
        DelvefoldServerLifecycle.register();
        GeologyAmbienceService.register();
        LandmarkDiscoveryService.register();
        NeoForge.EVENT_BUS.addListener(DelvefoldCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(EcosystemProfileReloadListener::register);
        NeoForge.EVENT_BUS.addListener(
                (AddReloadListenerEvent event) -> event.addListener(new LandmarkCatalogReloadListener()));
        NeoForge.EVENT_BUS.addListener(DelvefoldPermissions::onGatherNodes);
        NeoForge.EVENT_BUS.addListener(SpawnPolicy::onPositionCheck);
        NeoForge.EVENT_BUS.addListener(MiningPlayerSafety::onPlayerLoggedIn);
    }
}
