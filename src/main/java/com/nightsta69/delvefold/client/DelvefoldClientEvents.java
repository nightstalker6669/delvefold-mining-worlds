package com.nightsta69.delvefold.client;

import com.nightsta69.delvefold.network.DelvefoldNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Client-only lifecycle registration that installs payload handlers during NeoForge setup. */
public final class DelvefoldClientEvents {
    /** Prevents utility-class instantiation. */
    private DelvefoldClientEvents() {}

    /**
     * Registers the client setup listener on the mod event bus.
     *
     * @param modEventBus Delvefold mod lifecycle event bus
     */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(DelvefoldClientEvents::onClientSetup);
    }

    /**
     * Enqueues handler installation onto the client setup work queue.
     *
     * @param event NeoForge client setup event
     */
    private static void onClientSetup(FMLClientSetupEvent event) {
        var unused = event.enqueueWork(() -> DelvefoldNetwork.installClientHandlers(
                DelvefoldClientPayloadHandler::open,
                DelvefoldClientPayloadHandler::showResult,
                DelvefoldClientPayloadHandler::copyProfileExport,
                DelvefoldClientPayloadHandler::openGuide,
                DelvefoldClientPayloadHandler::openForecast,
                DelvefoldClientPayloadHandler::openOreImportScan,
                DelvefoldClientPayloadHandler::openOreImportPreview,
                DelvefoldClientPayloadHandler::openOreLibrary));
    }
}
