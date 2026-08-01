package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.network.ProtocolLimits;
import com.nightsta69.delvefold.network.codec.DelvefoldStreamCodecs;
import com.nightsta69.delvefold.network.model.ActionStatus;
import com.nightsta69.delvefold.network.service.DelvefoldAdminService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Clientbound outcome of a server-authoritative administration request.
 *
 * <p>The revision identifies the authoritative configuration state associated with the outcome; clients must use a
 * refreshed snapshot after a stale result rather than treating their submitted state as committed. The localized
 * message representation is bounded by {@link ProtocolLimits#MESSAGE_LENGTH} on the wire.
 *
 * @param status disposition assigned by the server
 * @param revision authoritative revision associated with the result
 * @param message bounded, client-displayable result message
 */
public record ActionResultPayload(ActionStatus status, long revision, String message) implements CustomPacketPayload {
    /** NeoForge payload type for the clientbound action result message. */
    public static final Type<ActionResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "action_result"));

    /** Wire codec encoding status, revision, and bounded message in that order. */
    public static final StreamCodec<RegistryFriendlyByteBuf, ActionResultPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                DelvefoldStreamCodecs.writeEnum(buffer, payload.status());
                buffer.writeLong(payload.revision());
                DelvefoldStreamCodecs.writeString(buffer, payload.message(), ProtocolLimits.MESSAGE_LENGTH);
            },
            buffer -> new ActionResultPayload(
                    DelvefoldStreamCodecs.readEnum(buffer, ActionStatus.class),
                    buffer.readLong(),
                    DelvefoldStreamCodecs.readString(buffer, ProtocolLimits.MESSAGE_LENGTH)));

    /**
     * Converts a service result into its clientbound representation, truncating an oversized message to the protocol
     * limit.
     *
     * @param result non-null server-side result to encode
     * @return payload containing the result status, revision, and bounded message
     * @throws NullPointerException if the result or its message is {@code null}
     */
    public static ActionResultPayload from(DelvefoldAdminService.ServiceResult result) {
        String message = result.message();
        if (message.length() > ProtocolLimits.MESSAGE_LENGTH) {
            message = message.substring(0, ProtocolLimits.MESSAGE_LENGTH);
        }
        return new ActionResultPayload(result.status(), result.revision(), message);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
