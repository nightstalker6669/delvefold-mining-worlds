package com.nightsta69.delvefold.portal;

import com.nightsta69.delvefold.config.AdminAccess;
import com.nightsta69.delvefold.config.ConfigSnapshot;
import com.nightsta69.delvefold.config.DelvefoldConfigService;
import com.nightsta69.delvefold.config.model.PortalSettings;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.reset.WorldOperationService;
import com.nightsta69.delvefold.world.DelvefoldWorldgen;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/** Resolves portal policy without ever opening initialization UI or mutating configuration. */
final class PortalAccess {
    static final int MIN_COOLDOWN_SECONDS = 1;
    static final int MAX_COOLDOWN_SECONDS = 3600;
    static final double MIN_COORDINATE_SCALE = 0.01D;
    static final double MAX_COORDINATE_SCALE = 100.0D;
    private static final long MESSAGE_INTERVAL_TICKS = 40L;
    private static final Map<UUID, Long> LAST_MESSAGE_TICK = new ConcurrentHashMap<>();

    private PortalAccess() {
    }

    static Result forIgnition(ServerLevel source, ServerPlayer player) {
        ConfigSnapshot snapshot = snapshot();
        if (snapshot == null) {
            return Result.denied(Component.translatable("message.delvefold.portal.config_unavailable"));
        }
        if (!snapshot.settings().initialized()) {
            return Result.denied(AdminAccess.canConfigure(player)
                    ? Component.translatable("message.delvefold.portal.uninitialized_admin")
                    : Component.translatable("message.delvefold.portal.uninitialized_player"));
        }
        if (!snapshot.settings().portal().enabled()) {
            return Result.denied(Component.translatable("message.delvefold.portal.disabled"));
        }
        return resolveDestination(source, snapshot.settings().terrainMode(),
                snapshot.settings().identity().terrainVariant(), snapshot.settings().portal(), true);
    }

    static Result forTransition(ServerLevel source, ServerPlayer player) {
        if (isMiningLevel(source.dimension())) {
            PortalSettings settings = settingsOrDefault();
            ServerLevel overworld = source.getServer().overworld();
            return Result.allowed(overworld, settings, true);
        }

        ConfigSnapshot snapshot = snapshot();
        if (snapshot == null) {
            return Result.denied(Component.translatable("message.delvefold.portal.config_unavailable"));
        }
        if (!snapshot.settings().initialized()) {
            return Result.denied(AdminAccess.canConfigure(player)
                    ? Component.translatable("message.delvefold.portal.uninitialized_admin")
                    : Component.translatable("message.delvefold.portal.uninitialized_player"));
        }
        if (!snapshot.settings().portal().enabled()) {
            return Result.denied(Component.translatable("message.delvefold.portal.disabled"));
        }
        return resolveDestination(source, snapshot.settings().terrainMode(),
                snapshot.settings().identity().terrainVariant(), snapshot.settings().portal(), true);
    }

    static int cooldownTicks(PortalSettings settings) {
        return Mth.clamp(settings.cooldownSeconds(), MIN_COOLDOWN_SECONDS, MAX_COOLDOWN_SECONDS) * 20;
    }

    static double coordinateScale(PortalSettings settings) {
        double scale = settings.coordinateScale();
        if (!Double.isFinite(scale) || scale <= 0.0D) {
            return 1.0D;
        }
        return Mth.clamp(scale, MIN_COORDINATE_SCALE, MAX_COORDINATE_SCALE);
    }

    static void notifyDenied(ServerPlayer player, Result result) {
        if (result.denial() == null) {
            return;
        }
        long gameTime = player.serverLevel().getGameTime();
        Long previous = LAST_MESSAGE_TICK.get(player.getUUID());
        if (previous == null || gameTime - previous >= MESSAGE_INTERVAL_TICKS || gameTime < previous) {
            LAST_MESSAGE_TICK.put(player.getUUID(), gameTime);
            player.sendSystemMessage(result.denial(), true);
        }
        if (LAST_MESSAGE_TICK.size() > 1024) {
            LAST_MESSAGE_TICK.entrySet().removeIf(entry -> gameTime - entry.getValue() > 1200L);
        }
    }

    static boolean isMiningLevel(ResourceKey<Level> dimension) {
        return DelvefoldWorldgen.isMiningLevel(dimension);
    }

    private static Result resolveDestination(
            ServerLevel source,
            TerrainMode activeTerrain,
            com.nightsta69.delvefold.config.model.TerrainVariant terrainVariant,
            PortalSettings settings,
            boolean enforceEntryBlock) {
        if (isMiningLevel(source.dimension())) {
            return Result.allowed(source.getServer().overworld(), settings, true);
        }
        if (settings.allowFromOverworldOnly() && !source.dimension().equals(Level.OVERWORLD)) {
            return Result.denied(Component.translatable("message.delvefold.portal.wrong_source"));
        }
        if (enforceEntryBlock && WorldOperationService.get().isEntryBlocked()) {
            return Result.denied(Component.translatable("message.delvefold.portal.reset_blocked"));
        }

        ServerLevel target = source.getServer().getLevel(DelvefoldWorldgen.levelFor(activeTerrain, terrainVariant));
        if (target == null) {
            return Result.denied(Component.translatable("message.delvefold.portal.destination_missing"));
        }
        return Result.allowed(target, settings, false);
    }

    @Nullable
    private static ConfigSnapshot snapshot() {
        try {
            return DelvefoldConfigService.get().snapshot();
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private static PortalSettings settingsOrDefault() {
        ConfigSnapshot snapshot = snapshot();
        return snapshot == null ? PortalSettings.defaults() : snapshot.settings().portal();
    }

    record Result(
            boolean allowed,
            @Nullable ServerLevel destination,
            PortalSettings settings,
            boolean returningToOverworld,
            @Nullable Component denial) {
        static Result allowed(ServerLevel destination, PortalSettings settings, boolean returningToOverworld) {
            return new Result(true, destination, settings, returningToOverworld, null);
        }

        static Result denied(Component denial) {
            return new Result(false, null, PortalSettings.defaults(), false, denial);
        }
    }
}
