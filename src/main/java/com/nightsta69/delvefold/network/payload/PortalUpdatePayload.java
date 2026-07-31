package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.config.model.PortalSettings;
import com.nightsta69.delvefold.network.codec.DelvefoldStreamCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PortalUpdatePayload(long expectedRevision, PortalSettings portal) implements CustomPacketPayload {
    public static final Type<PortalUpdatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("delvefold", "update_portal"));
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
