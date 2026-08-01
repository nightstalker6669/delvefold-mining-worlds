package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.network.ProtocolLimits;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OreImportScanPageRequestPayload(String scanToken, int page) implements CustomPacketPayload {
    public static final Type<OreImportScanPageRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "ore_import_scan_page"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OreImportScanPageRequestPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeUtf(payload.scanToken(), ProtocolLimits.MAX_IMPORT_TOKEN_LENGTH);
                        buffer.writeVarInt(payload.page());
                    },
                    buffer -> new OreImportScanPageRequestPayload(
                            buffer.readUtf(ProtocolLimits.MAX_IMPORT_TOKEN_LENGTH), buffer.readVarInt()));

    public OreImportScanPageRequestPayload {
        scanToken = requiredToken(scanToken);
        if (page < 0 || page > ProtocolLimits.MAX_IMPORT_GROUPS)
            throw new IllegalArgumentException("Invalid scan page");
    }

    private static String requiredToken(String value) {
        String safe = value == null ? "" : value.trim();
        if (safe.isEmpty() || safe.length() > ProtocolLimits.MAX_IMPORT_TOKEN_LENGTH)
            throw new IllegalArgumentException("Invalid scan token");
        return safe;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
