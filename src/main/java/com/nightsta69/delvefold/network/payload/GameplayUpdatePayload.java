package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.config.model.GameplaySettings;
import com.nightsta69.delvefold.network.codec.DelvefoldStreamCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Serverbound request to replace the authoritative gameplay settings.
 *
 * <p>The handler requires configure permission and compares the expected settings revision atomically before the server
 * validates and persists the supplied settings.
 *
 * @param expectedRevision settings revision on which the edit was based
 * @param gameplay proposed gameplay settings, subject to server-side validation
 */
public record GameplayUpdatePayload(long expectedRevision, GameplaySettings gameplay) implements CustomPacketPayload {
    /** NeoForge payload type for the serverbound gameplay-settings update. */
    public static final Type<GameplayUpdatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "update_gameplay"));

    /** Wire codec encoding the expected revision before the gameplay settings structure. */
    public static final StreamCodec<RegistryFriendlyByteBuf, GameplayUpdatePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeLong(payload.expectedRevision());
                DelvefoldStreamCodecs.writeGameplay(buffer, payload.gameplay());
            },
            buffer -> new GameplayUpdatePayload(buffer.readLong(), DelvefoldStreamCodecs.readGameplay(buffer)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
