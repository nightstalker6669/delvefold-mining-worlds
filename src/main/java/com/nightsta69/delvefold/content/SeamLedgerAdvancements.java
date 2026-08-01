package com.nightsta69.delvefold.content;

import com.nightsta69.delvefold.Delvefold;
import java.util.Objects;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Server-side advancement hooks for interactions with the Seam Ledger. */
public final class SeamLedgerAdvancements {
    public static final ResourceLocation CONSULT_LEDGER_ADVANCEMENT =
            ResourceLocation.fromNamespaceAndPath(Delvefold.MOD_ID, "consult_seam_ledger");
    public static final String CONSULT_CRITERION = "consult";

    private SeamLedgerAdvancements() {
    }

    /**
     * Records that a player successfully consulted the Seam Ledger.
     *
     * <p>This must be called on the logical server only after the player-bound client-open acknowledgement has been
     * validated and the current guide visibility has been rechecked.</p>
     *
     * @return {@code true} when the criterion was newly awarded; {@code false} if the advancement is unavailable or
     *         was already complete
     */
    public static boolean triggerConsulted(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        AdvancementHolder advancement = player.server.getAdvancements().get(CONSULT_LEDGER_ADVANCEMENT);
        return advancement != null && player.getAdvancements().award(advancement, CONSULT_CRITERION);
    }
}
