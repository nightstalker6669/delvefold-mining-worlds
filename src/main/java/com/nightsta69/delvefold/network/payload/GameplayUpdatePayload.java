package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.config.model.GameplaySettings;
import com.nightsta69.delvefold.network.codec.DelvefoldStreamCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record GameplayUpdatePayload(long expectedRevision, GameplaySettings gameplay) implements CustomPacketPayload {
    public static final Type<GameplayUpdatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "update_gameplay"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GameplayUpdatePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeLong(payload.expectedRevision());
                DelvefoldStreamCodecs.writeGameplay(buffer, payload.gameplay());
            },
            buffer -> new GameplayUpdatePayload(buffer.readLong(), DelvefoldStreamCodecs.readGameplay(buffer)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
