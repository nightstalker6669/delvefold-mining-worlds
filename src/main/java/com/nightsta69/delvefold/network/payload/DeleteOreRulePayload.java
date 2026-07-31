package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.network.ProtocolLimits;
import com.nightsta69.delvefold.network.codec.DelvefoldStreamCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DeleteOreRulePayload(long expectedRevision, String ruleId) implements CustomPacketPayload {
    public static final Type<DeleteOreRulePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("delvefold", "delete_ore_rule"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DeleteOreRulePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeLong(payload.expectedRevision());
                DelvefoldStreamCodecs.writeString(buffer, payload.ruleId(), ProtocolLimits.ID_LENGTH);
            },
            buffer -> new DeleteOreRulePayload(buffer.readLong(),
                    DelvefoldStreamCodecs.readString(buffer, ProtocolLimits.ID_LENGTH)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
