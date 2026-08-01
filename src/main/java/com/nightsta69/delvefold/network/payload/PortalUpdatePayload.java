package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.config.model.PortalSettings;
import com.nightsta69.delvefold.network.codec.DelvefoldStreamCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Serverbound request to replace the authoritative portal settings.
 *
 * <p>The handler requires configure permission and compares the expected settings revision atomically before the server
 * validates and persists the proposed settings.
 *
 * @param expectedRevision settings revision on which the edit was based
 * @param portal proposed portal settings, subject to server-side validation
 */
public record PortalUpdatePayload(long expectedRevision, PortalSettings portal) implements CustomPacketPayload {
    /** NeoForge payload type for the serverbound portal-settings update. */
    public static final Type<PortalUpdatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "update_portal"));

    /** Wire codec encoding the expected revision before the portal settings structure. */
    public static final StreamCodec<RegistryFriendlyByteBuf, PortalUpdatePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeLong(payload.expectedRevision());
                DelvefoldStreamCodecs.writePortal(buffer, payload.portal());
            },
            buffer -> new PortalUpdatePayload(buffer.readLong(), DelvefoldStreamCodecs.readPortal(buffer)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
