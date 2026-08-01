package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.network.ProtocolLimits;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OreImportPreviewPageRequestPayload(String commitToken, int page) implements CustomPacketPayload {
    public static final Type<OreImportPreviewPageRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "ore_import_preview_page"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OreImportPreviewPageRequestPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeUtf(payload.commitToken(), ProtocolLimits.MAX_IMPORT_TOKEN_LENGTH);
                        buffer.writeVarInt(payload.page());
                    },
                    buffer -> new OreImportPreviewPageRequestPayload(
                            buffer.readUtf(ProtocolLimits.MAX_IMPORT_TOKEN_LENGTH), buffer.readVarInt()));

    public OreImportPreviewPageRequestPayload {
        commitToken = commitToken == null ? "" : commitToken.trim();
        if (commitToken.isEmpty() || commitToken.length() > ProtocolLimits.MAX_IMPORT_TOKEN_LENGTH)
            throw new IllegalArgumentException("Invalid preview token");
        if (page < 0 || page > ProtocolLimits.MAX_IMPORT_GROUPS)
            throw new IllegalArgumentException("Invalid preview page");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
