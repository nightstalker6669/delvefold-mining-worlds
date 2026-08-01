package com.nightsta69.delvefold.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client acknowledgement sent only after the authorized guide screen is installed. */
public record GuideOpenedPayload(long authorizationId) implements CustomPacketPayload {
    public static final Type<GuideOpenedPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "guide_opened"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GuideOpenedPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeLong(payload.authorizationId()),
            buffer -> new GuideOpenedPayload(buffer.readLong()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
