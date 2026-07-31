package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.network.ProtocolLimits;
import com.nightsta69.delvefold.network.codec.DelvefoldStreamCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ProfileExportPayload(String profileId, String json) implements CustomPacketPayload {
    public static final Type<ProfileExportPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("delvefold", "profile_export"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ProfileExportPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                DelvefoldStreamCodecs.writeString(buffer, payload.profileId(), ProtocolLimits.ID_LENGTH);
                DelvefoldStreamCodecs.writeString(buffer, payload.json(), ProtocolLimits.MAX_PROFILE_CLIPBOARD_CHARS);
            },
            buffer -> new ProfileExportPayload(
                    DelvefoldStreamCodecs.readString(buffer, ProtocolLimits.ID_LENGTH),
                    DelvefoldStreamCodecs.readString(buffer, ProtocolLimits.MAX_PROFILE_CLIPBOARD_CHARS)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
