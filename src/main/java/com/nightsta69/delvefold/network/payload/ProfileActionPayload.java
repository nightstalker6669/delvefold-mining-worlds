package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.network.ProtocolLimits;
import com.nightsta69.delvefold.network.codec.DelvefoldStreamCodecs;
import com.nightsta69.delvefold.network.model.ProfileOperation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Serverbound request for an operation on authoritative ore profiles.
 *
 * <p>The server requires configure permission, atomically checks the expected ore revision, and interprets the bounded
 * identifiers, JSON, and overwrite flag according to the selected operation. All imported content is revalidated by the
 * server.
 *
 * @param expectedOreRevision ore-configuration revision on which the operation was based
 * @param operation profile operation to execute
 * @param sourceId bounded source profile identifier when required by the operation
 * @param targetId bounded target profile identifier when required by the operation
 * @param json bounded clipboard JSON when importing, or empty text otherwise
 * @param overwrite whether an operation may replace an existing target profile
 */
public record ProfileActionPayload(
        long expectedOreRevision,
        ProfileOperation operation,
        String sourceId,
        String targetId,
        String json,
        boolean overwrite)
        implements CustomPacketPayload {
    /** NeoForge payload type for the serverbound profile operation request. */
    public static final Type<ProfileActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "profile_action"));

    /**
     * Wire codec encoding ore revision, operation, source ID, target ID, bounded JSON, and overwrite flag in that
     * order.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, ProfileActionPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeLong(payload.expectedOreRevision());
                DelvefoldStreamCodecs.writeEnum(buffer, payload.operation());
                DelvefoldStreamCodecs.writeString(buffer, payload.sourceId(), ProtocolLimits.ID_LENGTH);
                DelvefoldStreamCodecs.writeString(buffer, payload.targetId(), ProtocolLimits.ID_LENGTH);
                DelvefoldStreamCodecs.writeString(buffer, payload.json(), ProtocolLimits.MAX_PROFILE_CLIPBOARD_CHARS);
                buffer.writeBoolean(payload.overwrite());
            },
            buffer -> new ProfileActionPayload(
                    buffer.readLong(),
                    DelvefoldStreamCodecs.readEnum(buffer, ProfileOperation.class),
                    DelvefoldStreamCodecs.readString(buffer, ProtocolLimits.ID_LENGTH),
                    DelvefoldStreamCodecs.readString(buffer, ProtocolLimits.ID_LENGTH),
                    DelvefoldStreamCodecs.readString(buffer, ProtocolLimits.MAX_PROFILE_CLIPBOARD_CHARS),
                    buffer.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
