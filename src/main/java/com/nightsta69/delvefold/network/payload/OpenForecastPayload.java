package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.config.analysis.OreProfileForecast;
import com.nightsta69.delvefold.network.codec.OreForecastStreamCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Clientbound response that opens or refreshes the read-only administrative forecast screen.
 *
 * <p>The server emits this bounded forecast only after authorizing and resolving a serverbound forecast request; the
 * enclosed model is informational and does not authorize a configuration mutation.
 *
 * @param forecast server-computed, paged forecast to display
 */
public record OpenForecastPayload(OreProfileForecast forecast) implements CustomPacketPayload {
    /** NeoForge payload type for the clientbound forecast view. */
    public static final Type<OpenForecastPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "open_forecast"));

    /** Wire codec for the bounded forecast structure. */
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenForecastPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> OreForecastStreamCodecs.write(buffer, payload.forecast()),
            buffer -> new OpenForecastPayload(OreForecastStreamCodecs.read(buffer)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
