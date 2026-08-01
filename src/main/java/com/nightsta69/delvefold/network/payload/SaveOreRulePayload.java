package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.network.codec.DelvefoldStreamCodecs;
import com.nightsta69.delvefold.network.model.AdminSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Serverbound request to create or replace an ore rule in the authoritative configuration.
 *
 * <p>The handler requires configure permission and compares the expected ore revision atomically before validating and
 * persisting the draft. The create-only flag lets callers reject an unintended overwrite on the server.
 *
 * @param expectedRevision ore-configuration revision on which the edit was based
 * @param rule proposed ore-rule draft, subject to server-side registry and numeric validation
 * @param createOnly whether the request must fail if the rule identifier already exists
 */
public record SaveOreRulePayload(long expectedRevision, AdminSnapshot.OreRuleDraft rule, boolean createOnly)
        implements CustomPacketPayload {
    /** NeoForge payload type for the serverbound ore-rule save request. */
    public static final Type<SaveOreRulePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "save_ore_rule"));

    /** Wire codec encoding the expected revision, bounded rule draft, and create-only flag in that order. */
    public static final StreamCodec<RegistryFriendlyByteBuf, SaveOreRulePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeLong(payload.expectedRevision());
                DelvefoldStreamCodecs.writeOreRule(buffer, payload.rule());
                buffer.writeBoolean(payload.createOnly());
            },
            buffer -> new SaveOreRulePayload(
                    buffer.readLong(), DelvefoldStreamCodecs.readOreRule(buffer), buffer.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
