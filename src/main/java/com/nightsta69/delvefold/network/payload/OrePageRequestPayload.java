package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.network.ProtocolLimits;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OrePageRequestPayload(int page, long knownOreRevision) implements CustomPacketPayload {
    public static final Type<OrePageRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("delvefold", "ore_page_request"));
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
