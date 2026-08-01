package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.network.ProtocolLimits;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Serverbound request for a bounded page of the authoritative ore-rule snapshot.
 *
 * <p>The server requires configure permission and returns current server state. The known revision identifies the
 * snapshot from which navigation originated but does not grant write authority.
 *
 * @param page zero-based ore-rule page, at most {@link ProtocolLimits#MAX_ORE_RULES}
 * @param knownOreRevision ore-configuration revision currently displayed by the requester
 */
public record OrePageRequestPayload(int page, long knownOreRevision) implements CustomPacketPayload {
    /** NeoForge payload type for the serverbound ore-rule page request. */
    public static final Type<OrePageRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "ore_page_request"));

    /**
     * Wire codec encoding the validated variable-length page before the known 64-bit ore revision; encoding and
     * decoding both reject pages outside the coarse protocol range.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, OrePageRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                if (payload.page() < 0 || payload.page() > ProtocolLimits.MAX_ORE_RULES) {
                    throw new IllegalArgumentException("Invalid ore page: " + payload.page());
                }
                buffer.writeVarInt(payload.page());
                buffer.writeLong(payload.knownOreRevision());
            },
            buffer -> {
                int page = buffer.readVarInt();
                if (page < 0 || page > ProtocolLimits.MAX_ORE_RULES) {
                    throw new IllegalArgumentException("Invalid ore page: " + page);
                }
                return new OrePageRequestPayload(page, buffer.readLong());
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
