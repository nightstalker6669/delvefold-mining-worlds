package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.network.ProtocolLimits;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

/**
 * Serverbound request for one bounded page of a server-authoritative profile forecast.
 *
 * <p>The server requires configure permission, resolves the profile from authoritative state, and returns only bounded
 * forecast data.
 *
 * @param profileId normalized profile identifier, bounded by {@link ProtocolLimits#ID_LENGTH}
 * @param page zero-based page index, at most {@link ProtocolLimits#MAX_ORE_RULES}
 */
public record ForecastRequestPayload(String profileId, int page) implements CustomPacketPayload {
    /** NeoForge payload type for the serverbound forecast request. */
    public static final Type<ForecastRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "forecast_request"));

    /** Wire codec encoding the bounded profile identifier before the variable-length page index. */
    public static final StreamCodec<RegistryFriendlyByteBuf, ForecastRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUtf(payload.profileId(), ProtocolLimits.ID_LENGTH);
                buffer.writeVarInt(payload.page());
            },
            buffer -> new ForecastRequestPayload(buffer.readUtf(ProtocolLimits.ID_LENGTH), buffer.readVarInt()));

    /**
     * Normalizes the profile identifier and enforces the request's protocol bounds.
     *
     * @param profileId profile identifier; {@code null} is normalized to empty text
     * @param page zero-based requested page
     * @throws IllegalArgumentException if the normalized identifier is too long or the page is outside the protocol
     *     range
     */
    public ForecastRequestPayload(@Nullable String profileId, int page) {
        profileId = profileId == null ? "" : profileId.trim();
        if (profileId.length() > ProtocolLimits.ID_LENGTH) {
            throw new IllegalArgumentException("Forecast profile ID is too long");
        }
        if (page < 0 || page > ProtocolLimits.MAX_ORE_RULES) {
            throw new IllegalArgumentException("Invalid forecast page: " + page);
        }
        this.profileId = profileId;
        this.page = page;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
