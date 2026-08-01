package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.network.ProtocolLimits;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Requests one bounded rule page of a server-authoritative profile forecast. */
public record ForecastRequestPayload(String profileId, int page) implements CustomPacketPayload {
    public static final Type<ForecastRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("delvefold", "forecast_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ForecastRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUtf(payload.profileId(), ProtocolLimits.ID_LENGTH);
                buffer.writeVarInt(payload.page());
            },
            buffer -> new ForecastRequestPayload(
                    buffer.readUtf(ProtocolLimits.ID_LENGTH), buffer.readVarInt()));

    public ForecastRequestPayload {
        profileId = profileId == null ? "" : profileId.trim();
        if (profileId.length() > ProtocolLimits.ID_LENGTH) {
            throw new IllegalArgumentException("Forecast profile ID is too long");
        }
        if (page < 0 || page > ProtocolLimits.MAX_ORE_RULES) {
            throw new IllegalArgumentException("Invalid forecast page: " + page);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
