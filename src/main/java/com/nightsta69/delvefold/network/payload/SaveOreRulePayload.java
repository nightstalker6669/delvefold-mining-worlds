package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.network.codec.DelvefoldStreamCodecs;
import com.nightsta69.delvefold.network.model.AdminSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SaveOreRulePayload(
        long expectedRevision,
        AdminSnapshot.OreRuleDraft rule,
        boolean createOnly)
        implements CustomPacketPayload {
    public static final Type<SaveOreRulePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("delvefold", "save_ore_rule"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SaveOreRulePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeLong(payload.expectedRevision());
                DelvefoldStreamCodecs.writeOreRule(buffer, payload.rule());
                buffer.writeBoolean(payload.createOnly());
            },
            buffer -> new SaveOreRulePayload(
                    buffer.readLong(),
                    DelvefoldStreamCodecs.readOreRule(buffer),
                    buffer.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
