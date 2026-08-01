package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.network.ProtocolLimits;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OreImportCreatePayload(String commitToken, String targetProfileId) implements CustomPacketPayload {
    public static final Type<OreImportCreatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "ore_import_create"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OreImportCreatePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUtf(payload.commitToken(), ProtocolLimits.MAX_IMPORT_TOKEN_LENGTH);
                buffer.writeUtf(payload.targetProfileId(), ProtocolLimits.ID_LENGTH);
            },
            buffer -> new OreImportCreatePayload(
                    buffer.readUtf(ProtocolLimits.MAX_IMPORT_TOKEN_LENGTH), buffer.readUtf(ProtocolLimits.ID_LENGTH)));

    public OreImportCreatePayload {
        commitToken = commitToken == null ? "" : commitToken.trim();
        targetProfileId = targetProfileId == null ? "" : targetProfileId.trim();
        if (commitToken.isEmpty() || commitToken.length() > ProtocolLimits.MAX_IMPORT_TOKEN_LENGTH)
            throw new IllegalArgumentException("Invalid preview token");
        if (targetProfileId.isEmpty() || targetProfileId.length() > ProtocolLimits.ID_LENGTH)
            throw new IllegalArgumentException("Invalid profile ID");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
