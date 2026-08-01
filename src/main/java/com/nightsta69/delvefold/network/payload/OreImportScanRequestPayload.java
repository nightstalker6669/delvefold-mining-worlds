package com.nightsta69.delvefold.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Serverbound request to start an authoritative scan for importable ore definitions.
 *
 * <p>The server requires configure permission and checks the supplied ore revision before creating a player-bound,
 * paged scan session.
 *
 * @param expectedOreRevision non-negative ore-configuration revision on which the scan request was based
 * @param includeVanilla whether vanilla ore definitions should be included in discovery
 */
public record OreImportScanRequestPayload(long expectedOreRevision, boolean includeVanilla)
        implements CustomPacketPayload {
    /** NeoForge payload type for the serverbound ore-import scan request. */
    public static final Type<OreImportScanRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "ore_import_scan"));

    /** Wire codec encoding the non-negative variable-length ore revision before the inclusion flag. */
    public static final StreamCodec<RegistryFriendlyByteBuf, OreImportScanRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarLong(payload.expectedOreRevision());
                buffer.writeBoolean(payload.includeVanilla());
            },
            buffer -> new OreImportScanRequestPayload(buffer.readVarLong(), buffer.readBoolean()));

    /**
     * Validates the optimistic-concurrency revision.
     *
     * @param expectedOreRevision ore-configuration revision on which the scan request was based
     * @param includeVanilla whether vanilla ore definitions should be included
     * @throws IllegalArgumentException if the revision is negative
     */
    public OreImportScanRequestPayload {
        if (expectedOreRevision < 0L) throw new IllegalArgumentException("Negative ore revision");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
