package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.network.codec.DelvefoldStreamCodecs;
import com.nightsta69.delvefold.network.model.AdminSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenGuiPayload(AdminSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<OpenGuiPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "open_gui"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenGuiPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> DelvefoldStreamCodecs.writeSnapshot(buffer, payload.snapshot()),
            buffer -> new OpenGuiPayload(DelvefoldStreamCodecs.readSnapshot(buffer)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
