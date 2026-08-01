package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.network.ProtocolLimits;
import com.nightsta69.delvefold.network.codec.DelvefoldStreamCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Clientbound export of a server-authoritative ore profile for the clipboard.
 *
 * <p>The server sends this response only after checking configure permission and bounds both the profile identifier and
 * serialized JSON. The export is data, not authorization to mutate server configuration.
 *
 * @param profileId identifier of the exported profile
 * @param json bounded serialized profile document
 */
public record ProfileExportPayload(String profileId, String json) implements CustomPacketPayload {
    /** NeoForge payload type for the clientbound profile export. */
    public static final Type<ProfileExportPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "profile_export"));

    /** Wire codec encoding the bounded profile identifier before the bounded JSON document. */
    public static final StreamCodec<RegistryFriendlyByteBuf, ProfileExportPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                DelvefoldStreamCodecs.writeString(buffer, payload.profileId(), ProtocolLimits.ID_LENGTH);
                DelvefoldStreamCodecs.writeString(buffer, payload.json(), ProtocolLimits.MAX_PROFILE_CLIPBOARD_CHARS);
            },
            buffer -> new ProfileExportPayload(
                    DelvefoldStreamCodecs.readString(buffer, ProtocolLimits.ID_LENGTH),
                    DelvefoldStreamCodecs.readString(buffer, ProtocolLimits.MAX_PROFILE_CLIPBOARD_CHARS)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
