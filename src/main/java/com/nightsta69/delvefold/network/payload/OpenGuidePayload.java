package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.guide.GuideSnapshot;
import com.nightsta69.delvefold.network.codec.GuideStreamCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Clientbound instruction to open a server-authorized guide snapshot.
 *
 * <p>The server first enforces the configured guide visibility policy and issues a player-bound, short-lived,
 * single-use authorization. The client echoes that identifier only after installing the screen.
 *
 * @param snapshot bounded, server-computed guide content
 * @param authorizationId opaque identifier to echo in {@link GuideOpenedPayload}
 */
public record OpenGuidePayload(GuideSnapshot snapshot, long authorizationId) implements CustomPacketPayload {
    /** NeoForge payload type for the clientbound guide-opening instruction. */
    public static final Type<OpenGuidePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "open_guide"));

    /** Wire codec encoding the guide snapshot before its 64-bit authorization identifier. */
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenGuidePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                GuideStreamCodecs.write(buffer, payload.snapshot());
                buffer.writeLong(payload.authorizationId());
            },
            buffer -> new OpenGuidePayload(GuideStreamCodecs.read(buffer), buffer.readLong()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
