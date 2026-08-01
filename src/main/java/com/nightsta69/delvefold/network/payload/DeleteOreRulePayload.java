package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.network.ProtocolLimits;
import com.nightsta69.delvefold.network.codec.DelvefoldStreamCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Serverbound request to delete an ore rule from the authoritative configuration.
 *
 * <p>The handler requires configure permission and uses the expected ore revision for optimistic concurrency before
 * validating and deleting the named rule.
 *
 * @param expectedRevision ore-configuration revision on which the deletion was based
 * @param ruleId bounded identifier of the rule to delete
 */
public record DeleteOreRulePayload(long expectedRevision, String ruleId) implements CustomPacketPayload {
    /** NeoForge payload type for the serverbound ore-rule deletion request. */
    public static final Type<DeleteOreRulePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "delete_ore_rule"));

    /** Wire codec encoding the expected revision before the bounded rule identifier. */
    public static final StreamCodec<RegistryFriendlyByteBuf, DeleteOreRulePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeLong(payload.expectedRevision());
                DelvefoldStreamCodecs.writeString(buffer, payload.ruleId(), ProtocolLimits.ID_LENGTH);
            },
            buffer -> new DeleteOreRulePayload(
                    buffer.readLong(), DelvefoldStreamCodecs.readString(buffer, ProtocolLimits.ID_LENGTH)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
