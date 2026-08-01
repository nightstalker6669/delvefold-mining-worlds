package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.network.ProtocolLimits;
import com.nightsta69.delvefold.network.codec.DelvefoldStreamCodecs;
import com.nightsta69.delvefold.network.model.ProfileOperation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ProfileActionPayload(
        long expectedOreRevision,
        ProfileOperation operation,
        String sourceId,
        String targetId,
        String json,
        boolean overwrite)
        implements CustomPacketPayload {
    public static final Type<ProfileActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "profile_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ProfileActionPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeLong(payload.expectedOreRevision());
                DelvefoldStreamCodecs.writeEnum(buffer, payload.operation());
                DelvefoldStreamCodecs.writeString(buffer, payload.sourceId(), ProtocolLimits.ID_LENGTH);
                DelvefoldStreamCodecs.writeString(buffer, payload.targetId(), ProtocolLimits.ID_LENGTH);
                DelvefoldStreamCodecs.writeString(buffer, payload.json(), ProtocolLimits.MAX_PROFILE_CLIPBOARD_CHARS);
                buffer.writeBoolean(payload.overwrite());
            },
            buffer -> new ProfileActionPayload(
                    buffer.readLong(),
                    DelvefoldStreamCodecs.readEnum(buffer, ProfileOperation.class),
                    DelvefoldStreamCodecs.readString(buffer, ProtocolLimits.ID_LENGTH),
                    DelvefoldStreamCodecs.readString(buffer, ProtocolLimits.ID_LENGTH),
                    DelvefoldStreamCodecs.readString(buffer, ProtocolLimits.MAX_PROFILE_CLIPBOARD_CHARS),
                    buffer.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
