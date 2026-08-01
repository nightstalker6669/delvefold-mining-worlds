package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.config.analysis.OreProfileForecast;
import com.nightsta69.delvefold.network.codec.OreForecastStreamCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Opens or refreshes the read-only administrative forecast screen. */
public record OpenForecastPayload(OreProfileForecast forecast) implements CustomPacketPayload {
    public static final Type<OpenForecastPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "open_forecast"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenForecastPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> OreForecastStreamCodecs.write(buffer, payload.forecast()),
            buffer -> new OpenForecastPayload(OreForecastStreamCodecs.read(buffer)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
