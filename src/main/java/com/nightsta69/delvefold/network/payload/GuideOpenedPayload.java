package com.nightsta69.delvefold.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Serverbound acknowledgement sent only after the authorized guide screen is installed.
 *
 * <p>The identifier is a short-lived, player-bound, single-use authorization issued by the server. Receipt alone does
 * not grant access: the server consumes the authorization and rechecks the current guide visibility policy before
 * awarding consultation credit.
 *
 * @param authorizationId opaque authorization supplied by the matching {@link OpenGuidePayload}
 */
public record GuideOpenedPayload(long authorizationId) implements CustomPacketPayload {
    /** NeoForge payload type for the serverbound guide-open acknowledgement. */
    public static final Type<GuideOpenedPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "guide_opened"));

    /** Wire codec for the opaque 64-bit authorization identifier. */
    public static final StreamCodec<RegistryFriendlyByteBuf, GuideOpenedPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeLong(payload.authorizationId()),
            buffer -> new GuideOpenedPayload(buffer.readLong()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
