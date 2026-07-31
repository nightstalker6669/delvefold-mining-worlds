package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.config.model.WorldIdentitySettings;
import com.nightsta69.delvefold.network.codec.DelvefoldStreamCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record IdentityUpdatePayload(long expectedRevision, WorldIdentitySettings identity)
        implements CustomPacketPayload {
    public static final Type<IdentityUpdatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("delvefold", "identity_update"));
    public static final StreamCodec<RegistryFriendlyByteBuf, IdentityUpdatePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeLong(payload.expectedRevision());
                DelvefoldStreamCodecs.writeIdentity(buffer, payload.identity());
            },
            buffer -> new IdentityUpdatePayload(buffer.readLong(), DelvefoldStreamCodecs.readIdentity(buffer)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
