package com.nightsta69.delvefold.client;

import com.nightsta69.delvefold.network.DelvefoldNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

public final class DelvefoldClientEvents {
    private DelvefoldClientEvents() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(DelvefoldClientEvents::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> DelvefoldNetwork.installClientHandlers(
                DelvefoldClientPayloadHandler::open,
                DelvefoldClientPayloadHandler::showResult,
                DelvefoldClientPayloadHandler::copyProfileExport));
    }
}
