package com.nightsta69.delvefold.world.landmark;

import com.nightsta69.delvefold.Delvefold;
import com.nightsta69.delvefold.api.event.DelvefoldLandmarkDiscoveredEvent;
import com.nightsta69.delvefold.world.DelvefoldWorldgen;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.Nullable;

/** Bounded server-side enter detection shared by the advancement and public discovery event. */
public final class LandmarkDiscoveryService {
    /** Advancement ID awarded after the server observes entry into a landmark piece. */
    public static final ResourceLocation ADVANCEMENT =
            ResourceLocation.fromNamespaceAndPath(Delvefold.MOD_ID, "discover_landmark");
    /** Criterion name awarded within {@link #ADVANCEMENT}. */
    public static final String CRITERION = "discover";

    private static final Map<UUID, Visit> CURRENT_VISITS = new ConcurrentHashMap<>();
    private static boolean registered;

    private LandmarkDiscoveryService() {}

    /**
     * Registers idempotent server-side discovery listeners on NeoForge's gameplay bus.
     *
     * <p>Player positions are sampled every 20 ticks (one second at the normal 20 ticks per second). Visit state is
     * removed on logout and server stop; neither coordinates nor visits are persisted.
     */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(PlayerTickEvent.Post.class, LandmarkDiscoveryService::onPlayerTick);
        bus.addListener(
                PlayerEvent.PlayerLoggedOutEvent.class,
                event -> CURRENT_VISITS.remove(event.getEntity().getUUID()));
        bus.addListener(ServerStoppingEvent.class, event -> CURRENT_VISITS.clear());
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) {
            return;
        }
        if (!DelvefoldWorldgen.isMiningLevel(player.level().dimension())) {
            CURRENT_VISITS.remove(player.getUUID());
            return;
        }
        ServerLevel level = player.serverLevel();
        StructureStart start =
                level.structureManager().getStructureWithPieceAt(player.blockPosition(), LandmarkRegistries.LANDMARKS);
        if (!start.isValid()) {
            CURRENT_VISITS.remove(player.getUUID());
            return;
        }
        LandmarkTemplatePiece piece = start.getPieces().stream()
                .filter(candidate -> candidate.getBoundingBox().isInside(player.blockPosition()))
                .filter(LandmarkTemplatePiece.class::isInstance)
                .map(LandmarkTemplatePiece.class::cast)
                .findFirst()
                .orElse(null);
        if (piece == null) {
            CURRENT_VISITS.remove(player.getUUID());
            return;
        }
        Visit visit = new Visit(level.dimension(), start.getChunkPos(), piece.landmarkId());
        @Nullable Visit previous = CURRENT_VISITS.put(player.getUUID(), visit);
        if (!isVisitTransition(previous, visit)) {
            return;
        }
        award(player);
        NeoForge.EVENT_BUS.post(new DelvefoldLandmarkDiscoveredEvent(
                player, piece.landmarkId(), level.dimension(), start.getChunkPos()));
    }

    private static boolean award(ServerPlayer player) {
        AdvancementHolder advancement = player.server.getAdvancements().get(ADVANCEMENT);
        return advancement != null && player.getAdvancements().award(advancement, CRITERION);
    }

    static void resetForTests() {
        CURRENT_VISITS.clear();
    }

    static boolean isVisitTransition(@Nullable Visit previous, @Nullable Visit current) {
        return current != null && !current.equals(previous);
    }

    record Visit(ResourceKey<Level> dimension, ChunkPos startChunk, ResourceLocation landmarkId) {}
}
