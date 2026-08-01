package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.config.model.WorldIdentitySettings;
import com.nightsta69.delvefold.network.codec.DelvefoldStreamCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Serverbound request to replace the authoritative world identity settings.
 *
 * <p>The handler requires configure permission and compares the expected settings revision before the server validates
 * and persists the proposed identity.
 *
 * @param expectedRevision settings revision on which the edit was based
 * @param identity proposed world identity, subject to server-side validation
 */
public record IdentityUpdatePayload(long expectedRevision, WorldIdentitySettings identity)
        implements CustomPacketPayload {
    /** NeoForge payload type for the serverbound identity-settings update. */
    public static final Type<IdentityUpdatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "identity_update"));

    /** Wire codec encoding the expected revision before the identity settings structure. */
    public static final StreamCodec<RegistryFriendlyByteBuf, IdentityUpdatePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeLong(payload.expectedRevision());
                DelvefoldStreamCodecs.writeIdentity(buffer, payload.identity());
            },
            buffer -> new IdentityUpdatePayload(buffer.readLong(), DelvefoldStreamCodecs.readIdentity(buffer)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
