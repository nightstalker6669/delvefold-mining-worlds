package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.network.ProtocolLimits;
import com.nightsta69.delvefold.network.codec.DelvefoldStreamCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ProfileExportRequestPayload(String profileId) implements CustomPacketPayload {
    public static final Type<ProfileExportRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("delvefold", "profile_export_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ProfileExportRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> DelvefoldStreamCodecs.writeString(buffer, payload.profileId(), ProtocolLimits.ID_LENGTH),
            buffer -> new ProfileExportRequestPayload(
                    DelvefoldStreamCodecs.readString(buffer, ProtocolLimits.ID_LENGTH)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
