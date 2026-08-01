package com.nightsta69.delvefold.portal;

import com.nightsta69.delvefold.config.DelvefoldConfigService;
import com.nightsta69.delvefold.config.model.PortalSettings;
import com.nightsta69.delvefold.world.DelvefoldWorldgen;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import org.jspecify.annotations.Nullable;

/** NeoForge adapter that applies central-hub protection to player and environmental mutations. */
public final class CentralHubProtectionEvents {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private CentralHubProtectionEvents() {}

    /** Registers once on the game bus; call from the common mod bootstrap. */
    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(CentralHubProtectionEvents::onBreak);
        NeoForge.EVENT_BUS.addListener(CentralHubProtectionEvents::onPlace);
        NeoForge.EVENT_BUS.addListener(CentralHubProtectionEvents::onMultiPlace);
        NeoForge.EVENT_BUS.addListener(CentralHubProtectionEvents::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(CentralHubProtectionEvents::onFluidPlace);
        NeoForge.EVENT_BUS.addListener(CentralHubProtectionEvents::onFarmlandTrample);
        NeoForge.EVENT_BUS.addListener(CentralHubProtectionEvents::onLivingDestroyBlock);
        NeoForge.EVENT_BUS.addListener(CentralHubProtectionEvents::onPiston);
        NeoForge.EVENT_BUS.addListener(CentralHubProtectionEvents::onExplosion);
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        PortalSettings settings = settings();
        if (settings != null) {
            applyBreak(event, settings, DelvefoldWorldgen.isMiningLevel(level.dimension()));
        }
    }

    static void applyBreak(BlockEvent.BreakEvent event, PortalSettings settings, boolean miningLevel) {
        if (deny(event.getPos(), event.getPlayer(), settings, miningLevel)) {
            event.setCanceled(true);
        }
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event instanceof BlockEvent.EntityMultiPlaceEvent || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        PortalSettings settings = settings();
        if (settings != null) {
            applyPlace(event, settings, DelvefoldWorldgen.isMiningLevel(level.dimension()));
        }
    }

    static void applyPlace(BlockEvent.EntityPlaceEvent event, PortalSettings settings, boolean miningLevel) {
        if (deny(event.getPos(), event.getEntity(), settings, miningLevel)) {
            event.setCanceled(true);
        }
    }

    private static void onMultiPlace(BlockEvent.EntityMultiPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        boolean protectedPosition = event.getReplacedBlockSnapshots().stream()
                .map(snapshot -> snapshot.getPos())
                .anyMatch(position -> denied(level, position, event.getEntity()));
        if (protectedPosition) {
            notifyDenied(event.getEntity());
            event.setCanceled(true);
        }
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer)) {
            return;
        }
        PortalSettings settings = settings();
        if (settings != null) {
            applyRightClick(event, settings, DelvefoldWorldgen.isMiningLevel(level.dimension()));
        }
    }

    static void applyRightClick(
            PlayerInteractEvent.RightClickBlock event, PortalSettings settings, boolean miningLevel) {
        if (event.getEntity() instanceof ServerPlayer player
                && !CentralHubProtectionService.mayModify(player, event.getPos(), settings, miningLevel)) {
            notifyDenied(player);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    private static void onFluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
        if (event.getLevel() instanceof ServerLevel level
                && (protectedAt(level, event.getPos()) || protectedAt(level, event.getLiquidPos()))) {
            event.setCanceled(true);
        }
    }

    private static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getLevel() instanceof ServerLevel level && deny(level, event.getPos(), event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private static void onLivingDestroyBlock(LivingDestroyBlockEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level && deny(level, event.getPos(), event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private static void onPiston(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        boolean intersects = protectedAt(level, event.getPos()) || protectedAt(level, event.getFaceOffsetPos());
        var resolver = event.getStructureHelper();
        if (!intersects && resolver != null && resolver.resolve()) {
            intersects = resolver.getToPush().stream()
                            .anyMatch(position -> protectedAt(level, position)
                                    || protectedAt(level, position.relative(event.getDirection())))
                    || resolver.getToDestroy().stream().anyMatch(position -> protectedAt(level, position));
        }
        if (intersects) {
            event.setCanceled(true);
        }
    }

    private static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        event.getAffectedBlocks().removeIf(position -> protectedAt(level, position));
    }

    private static boolean deny(ServerLevel level, BlockPos position, @Nullable Entity actor) {
        if (!denied(level, position, actor)) {
            return false;
        }
        notifyDenied(actor);
        return true;
    }

    private static boolean deny(
            BlockPos position, @Nullable Entity actor, PortalSettings settings, boolean miningLevel) {
        if (!denied(position, actor, settings, miningLevel)) {
            return false;
        }
        notifyDenied(actor);
        return true;
    }

    private static boolean denied(ServerLevel level, BlockPos position, @Nullable Entity actor) {
        PortalSettings settings = settings();
        if (settings == null || !CentralHubProtectionService.isProtected(level, position, settings)) {
            return false;
        }
        return !(actor instanceof ServerPlayer player)
                || !CentralHubProtectionService.mayModify(player, level, position, settings);
    }

    private static boolean denied(
            BlockPos position, @Nullable Entity actor, PortalSettings settings, boolean miningLevel) {
        if (!CentralHubProtectionService.isProtected(position, settings, miningLevel)) {
            return false;
        }
        return !(actor instanceof ServerPlayer player)
                || !CentralHubProtectionService.mayModify(player, position, settings, miningLevel);
    }

    private static boolean protectedAt(ServerLevel level, BlockPos position) {
        PortalSettings settings = settings();
        return settings != null && CentralHubProtectionService.isProtected(level, position, settings);
    }

    private static @Nullable PortalSettings settings() {
        try {
            return DelvefoldConfigService.get().snapshot().settings().portal();
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private static void notifyDenied(@Nullable Entity entity) {
        if (entity instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.translatable("message.delvefold.portal.permission_denied"), true);
        }
    }
}
