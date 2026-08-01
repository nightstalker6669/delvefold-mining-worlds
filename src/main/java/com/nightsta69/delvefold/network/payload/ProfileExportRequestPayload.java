package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.network.ProtocolLimits;
import com.nightsta69.delvefold.network.codec.DelvefoldStreamCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Serverbound request to export one authoritative ore profile.
 *
 * <p>The server requires configure permission, resolves the bounded identifier against current server state, and
 * refuses exports whose serialized representation exceeds the clipboard protocol limit.
 *
 * @param profileId bounded identifier of the profile to export
 */
public record ProfileExportRequestPayload(String profileId) implements CustomPacketPayload {
    /** NeoForge payload type for the serverbound profile export request. */
    public static final Type<ProfileExportRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "profile_export_request"));

    /** Wire codec for the profile identifier bounded by {@link ProtocolLimits#ID_LENGTH}. */
    public static final StreamCodec<RegistryFriendlyByteBuf, ProfileExportRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) ->
                    DelvefoldStreamCodecs.writeString(buffer, payload.profileId(), ProtocolLimits.ID_LENGTH),
            buffer -> new ProfileExportRequestPayload(
                    DelvefoldStreamCodecs.readString(buffer, ProtocolLimits.ID_LENGTH)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
