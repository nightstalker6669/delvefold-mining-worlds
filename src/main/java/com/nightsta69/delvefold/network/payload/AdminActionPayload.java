package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.network.ProtocolLimits;
import com.nightsta69.delvefold.network.codec.DelvefoldStreamCodecs;
import com.nightsta69.delvefold.network.model.AdminOperation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AdminActionPayload(long expectedRevision, AdminOperation operation, String confirmation)
        implements CustomPacketPayload {
    public static final Type<AdminActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("delvefold", "admin_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AdminActionPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeLong(payload.expectedRevision());
                DelvefoldStreamCodecs.writeEnum(buffer, payload.operation());
                DelvefoldStreamCodecs.writeString(buffer, payload.confirmation(), ProtocolLimits.SHORT_TEXT_LENGTH);
            },
            buffer -> new AdminActionPayload(
                    buffer.readLong(),
                    DelvefoldStreamCodecs.readEnum(buffer, AdminOperation.class),
                    DelvefoldStreamCodecs.readString(buffer, ProtocolLimits.SHORT_TEXT_LENGTH)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
