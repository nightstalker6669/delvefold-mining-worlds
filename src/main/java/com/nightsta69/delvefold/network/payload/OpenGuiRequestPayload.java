package com.nightsta69.delvefold.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenGuiRequestPayload() implements CustomPacketPayload {
    public static final Type<OpenGuiRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "open_gui_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenGuiRequestPayload> STREAM_CODEC =
            StreamCodec.of((buffer, payload) -> {}, buffer -> new OpenGuiRequestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
