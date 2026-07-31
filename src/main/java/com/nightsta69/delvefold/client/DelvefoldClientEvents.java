package com.nightsta69.delvefold.client;

import com.nightsta69.delvefold.network.DelvefoldNetwork;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = "delvefold", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DelvefoldClientEvents {
    private DelvefoldClientEvents() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> DelvefoldNetwork.installClientHandlers(
                DelvefoldClientPayloadHandler::open,
                DelvefoldClientPayloadHandler::showResult));
    }
}
