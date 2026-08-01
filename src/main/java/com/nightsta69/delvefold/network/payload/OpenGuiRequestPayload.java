package com.nightsta69.delvefold.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Empty serverbound request to open the administration screen at its initial page.
 *
 * <p>The server checks configure permission before replying with an authoritative {@link OpenGuiPayload}; this message
 * carries no state and conveys no authority by itself.
 */
public record OpenGuiRequestPayload() implements CustomPacketPayload {
    /** NeoForge payload type for the serverbound administration-screen request. */
    public static final Type<OpenGuiRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "open_gui_request"));

    /** Zero-field wire codec; the payload body is intentionally empty. */
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenGuiRequestPayload> STREAM_CODEC =
            StreamCodec.of((buffer, payload) -> {}, buffer -> new OpenGuiRequestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
