package com.nightsta69.delvefold;

import com.nightsta69.delvefold.command.DelvefoldCommands;
import com.nightsta69.delvefold.client.DelvefoldClientEvents;
import com.nightsta69.delvefold.admin.DefaultDelvefoldAdminService;
import com.nightsta69.delvefold.gameplay.SpawnPolicy;
import com.nightsta69.delvefold.config.EcosystemProfileReloadListener;
import com.nightsta69.delvefold.config.DelvefoldPermissions;
import com.nightsta69.delvefold.network.DelvefoldNetwork;
import com.nightsta69.delvefold.network.service.DelvefoldAdminServices;
import com.nightsta69.delvefold.portal.PortalRegistries;
import com.nightsta69.delvefold.reset.MiningPlayerSafety;
import com.nightsta69.delvefold.server.DelvefoldServerLifecycle;
import com.nightsta69.delvefold.world.DelvefoldWorldgen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(Delvefold.MOD_ID)
public final class Delvefold {
    public static final String MOD_ID = "delvefold";

    public Delvefold(IEventBus modEventBus, ModContainer modContainer, Dist dist) {
        DelvefoldWorldgen.register(modEventBus);
        PortalRegistries.register(modEventBus);
        DelvefoldNetwork.register(modEventBus);
        if (dist == Dist.CLIENT) {
            DelvefoldClientEvents.register(modEventBus);
        }
        DelvefoldAdminServices.install(new DefaultDelvefoldAdminService());
        DelvefoldServerLifecycle.register();
        NeoForge.EVENT_BUS.addListener(DelvefoldCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(EcosystemProfileReloadListener::register);
        NeoForge.EVENT_BUS.addListener(DelvefoldPermissions::onGatherNodes);
        NeoForge.EVENT_BUS.addListener(SpawnPolicy::onPositionCheck);
        NeoForge.EVENT_BUS.addListener(MiningPlayerSafety::onPlayerLoggedIn);
    }
}
