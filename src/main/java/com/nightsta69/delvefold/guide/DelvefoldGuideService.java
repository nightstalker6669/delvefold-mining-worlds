package com.nightsta69.delvefold.guide;

import com.mojang.logging.LogUtils;
import com.nightsta69.delvefold.config.AdminAccess;
import com.nightsta69.delvefold.config.ConfigSnapshot;
import com.nightsta69.delvefold.config.DelvefoldConfigService;
import com.nightsta69.delvefold.content.SeamLedgerAdvancements;
import com.nightsta69.delvefold.network.payload.OpenGuidePayload;
import com.nightsta69.delvefold.reset.WorldOperationService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

/** Server-authoritative opening path shared by the ledger item and command. */
public final class DelvefoldGuideService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final GuideOpenAuthorizations OPEN_AUTHORIZATIONS = new GuideOpenAuthorizations();

    private DelvefoldGuideService() {
    }

    public static OpenResult openFor(ServerPlayer player) {
        ConfigSnapshot config;
        try {
            config = DelvefoldConfigService.get().snapshot();
        } catch (IllegalStateException exception) {
            return OpenResult.denied(Component.translatable("message.delvefold.guide.unavailable"));
        }

        boolean operator = AdminAccess.canConfigure(player);
        if (!GuideAccessPolicy.allows(config.settings().guideVisibility(), operator)) {
            String key = config.settings().guideVisibility()
                    == com.nightsta69.delvefold.config.model.GuideVisibility.DISABLED
                    ? "message.delvefold.guide.disabled"
                    : "message.delvefold.guide.operators_only";
            return OpenResult.denied(Component.translatable(key));
        }

        try {
            GuideSnapshot snapshot = GuideSnapshotBuilder.build(
                    config, System.currentTimeMillis(), WorldOperationService.get().isEntryBlocked());
            long authorizationId = OPEN_AUTHORIZATIONS.issue(
                    player.getUUID(), player.getServer().getTickCount());
            try {
                PacketDistributor.sendToPlayer(player, new OpenGuidePayload(snapshot, authorizationId));
            } catch (RuntimeException exception) {
                OPEN_AUTHORIZATIONS.discard(player.getUUID(), authorizationId);
                throw exception;
            }
            return OpenResult.success();
        } catch (RuntimeException exception) {
            LOGGER.error("Could not open the Delvefold guide for {}", player.getGameProfile().getName(), exception);
            return OpenResult.denied(Component.translatable("message.delvefold.guide.unavailable"));
        }
    }

    /** Awards consultation only for the one client opening that the server just authorized. */
    public static boolean confirmOpened(ServerPlayer player, long authorizationId) {
        if (!OPEN_AUTHORIZATIONS.confirm(
                player.getUUID(), authorizationId, player.getServer().getTickCount())) {
            return false;
        }
        try {
            ConfigSnapshot current = DelvefoldConfigService.get().snapshot();
            if (!GuideAccessPolicy.allows(current.settings().guideVisibility(), AdminAccess.canConfigure(player))) {
                return false;
            }
        } catch (IllegalStateException exception) {
            return false;
        }
        return SeamLedgerAdvancements.triggerConsulted(player);
    }

    public record OpenResult(boolean opened, Component message) {
        private static OpenResult success() {
            return new OpenResult(true, Component.empty());
        }

        private static OpenResult denied(Component message) {
            return new OpenResult(false, message);
        }
    }
}
