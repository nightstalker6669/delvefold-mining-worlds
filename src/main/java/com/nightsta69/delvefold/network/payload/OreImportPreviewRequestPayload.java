package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.network.ProtocolLimits;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OreImportPreviewRequestPayload(String scanToken, List<String> selectedGroupIds)
        implements CustomPacketPayload {
    public static final Type<OreImportPreviewRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "ore_import_preview"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OreImportPreviewRequestPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeUtf(payload.scanToken(), ProtocolLimits.MAX_IMPORT_TOKEN_LENGTH);
                        buffer.writeVarInt(payload.selectedGroupIds().size());
                        payload.selectedGroupIds().forEach(id -> buffer.writeUtf(id, ProtocolLimits.ID_LENGTH));
                    },
                    buffer -> {
                        String token = buffer.readUtf(ProtocolLimits.MAX_IMPORT_TOKEN_LENGTH);
                        int count = buffer.readVarInt();
                        if (count < 1 || count > ProtocolLimits.MAX_IMPORT_SELECTED_GROUPS)
                            throw new IllegalArgumentException("Invalid selected group count");
                        java.util.ArrayList<String> ids = new java.util.ArrayList<>(count);
                        for (int index = 0; index < count; index++) ids.add(buffer.readUtf(ProtocolLimits.ID_LENGTH));
                        return new OreImportPreviewRequestPayload(token, ids);
                    });

    public OreImportPreviewRequestPayload {
        scanToken = scanToken == null ? "" : scanToken.trim();
        if (scanToken.isEmpty() || scanToken.length() > ProtocolLimits.MAX_IMPORT_TOKEN_LENGTH)
            throw new IllegalArgumentException("Invalid scan token");
        selectedGroupIds = selectedGroupIds == null
                ? List.of()
                : selectedGroupIds.stream()
                        .map(id -> id == null ? "" : id.trim())
                        .toList();
        if (selectedGroupIds.isEmpty()
                || selectedGroupIds.size() > ProtocolLimits.MAX_IMPORT_SELECTED_GROUPS
                || selectedGroupIds.stream()
                        .anyMatch(id -> id == null || id.isBlank() || id.length() > ProtocolLimits.ID_LENGTH)
                || selectedGroupIds.stream().distinct().count() != selectedGroupIds.size()) {
            throw new IllegalArgumentException("Invalid selected ore groups");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
