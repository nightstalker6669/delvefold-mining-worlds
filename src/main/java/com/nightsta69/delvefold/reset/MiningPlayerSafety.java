package com.nightsta69.delvefold.reset;

import com.nightsta69.delvefold.config.ConfigSnapshot;
import com.nightsta69.delvefold.config.DelvefoldConfigService;
import com.nightsta69.delvefold.world.DelvefoldWorldgen;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Prevents offline players from reappearing inside deleted or regenerated mining terrain. */
public final class MiningPlayerSafety {
    private static final String GENERATION_EPOCH_TAG = "DelvefoldGenerationEpoch";

    private MiningPlayerSafety() {}

    /** Called after a successful portal transition into the active mining world. */
    public static void markCurrentEpoch(ServerPlayer player) {
        try {
            ConfigSnapshot snapshot = DelvefoldConfigService.get().snapshot();
            if (snapshot.settings().initialized()) {
                player.getPersistentData()
                        .putLong(GENERATION_EPOCH_TAG, snapshot.settings().generationEpoch());
            }
        } catch (IllegalStateException ignored) {
            // An absent tag is fail-safe: the player will be evacuated on their next login.
        }
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !isMiningLevel(player.level().dimension())) {
            return;
        }

        ConfigSnapshot snapshot;
        try {
            snapshot = DelvefoldConfigService.get().snapshot();
        } catch (IllegalStateException ignored) {
            evacuate(player, "message.delvefold.player_safety.config_unavailable");
            return;
        }

        var settings = snapshot.settings();
        boolean epochMatches = player.getPersistentData().contains(GENERATION_EPOCH_TAG, Tag.TAG_LONG)
                && player.getPersistentData().getLong(GENERATION_EPOCH_TAG) == settings.generationEpoch();
        boolean activeDimension = settings.initialized()
                && player.level()
                        .dimension()
                        .equals(DelvefoldWorldgen.levelFor(
                                settings.terrainMode(), settings.identity().terrainVariant()));
        if (WorldOperationService.get().isEntryBlocked() || !activeDimension || !epochMatches) {
            evacuate(player, "message.delvefold.player_safety.evacuated");
        }
    }

    static void evacuate(ServerPlayer player) {
        evacuate(player, null);
    }

    private static void evacuate(ServerPlayer player, String messageKey) {
        ServerLevel overworld = player.getServer().overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        player.teleportTo(
                overworld,
                spawn.getX() + 0.5D,
                spawn.getY() + 1.0D,
                spawn.getZ() + 0.5D,
                Set.<RelativeMovement>of(),
                player.getYRot(),
                player.getXRot());
        if (messageKey != null) {
            player.sendSystemMessage(Component.translatable(messageKey));
        }
    }

    private static boolean isMiningLevel(ResourceKey<Level> key) {
        return DelvefoldWorldgen.isMiningLevel(key);
    }
}
