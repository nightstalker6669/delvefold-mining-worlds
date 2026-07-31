package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.network.ProtocolLimits;
import com.nightsta69.delvefold.network.codec.DelvefoldStreamCodecs;
import com.nightsta69.delvefold.network.model.ActionStatus;
import com.nightsta69.delvefold.network.service.DelvefoldAdminService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ActionResultPayload(ActionStatus status, long revision, String message)
        implements CustomPacketPayload {
    public static final Type<ActionResultPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("delvefold", "action_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ActionResultPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                DelvefoldStreamCodecs.writeEnum(buffer, payload.status());
                buffer.writeLong(payload.revision());
                DelvefoldStreamCodecs.writeString(buffer, payload.message(), ProtocolLimits.MESSAGE_LENGTH);
            },
            buffer -> new ActionResultPayload(
                    DelvefoldStreamCodecs.readEnum(buffer, ActionStatus.class),
                    buffer.readLong(),
                    DelvefoldStreamCodecs.readString(buffer, ProtocolLimits.MESSAGE_LENGTH)));

    public static ActionResultPayload from(DelvefoldAdminService.ServiceResult result) {
        String message = result.message();
        if (message.length() > ProtocolLimits.MESSAGE_LENGTH) {
            message = message.substring(0, ProtocolLimits.MESSAGE_LENGTH);
        }
        return new ActionResultPayload(result.status(), result.revision(), message);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
