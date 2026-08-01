package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.guide.GuideSnapshot;
import com.nightsta69.delvefold.network.codec.GuideStreamCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenGuidePayload(GuideSnapshot snapshot, long authorizationId) implements CustomPacketPayload {
    public static final Type<OpenGuidePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "open_guide"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenGuidePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                GuideStreamCodecs.write(buffer, payload.snapshot());
                buffer.writeLong(payload.authorizationId());
            },
            buffer -> new OpenGuidePayload(GuideStreamCodecs.read(buffer), buffer.readLong()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
