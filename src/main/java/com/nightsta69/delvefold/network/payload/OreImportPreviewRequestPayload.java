package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.network.ProtocolLimits;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

/**
 * Serverbound request to preview selected groups from a previously authorized ore scan.
 *
 * <p>The server requires configure permission and validates the player-bound scan token, selected identifiers, session
 * binding, and authoritative ore revision before issuing a preview.
 *
 * @param scanToken normalized server-issued scan token
 * @param selectedGroupIds normalized, distinct group identifiers to include, limited by
 *     {@link ProtocolLimits#MAX_IMPORT_SELECTED_GROUPS}
 */
public record OreImportPreviewRequestPayload(String scanToken, List<String> selectedGroupIds)
        implements CustomPacketPayload {
    /** NeoForge payload type for the serverbound ore-import preview request. */
    public static final Type<OreImportPreviewRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "ore_import_preview"));

    /**
     * Wire codec encoding the bounded scan token, selected-group count, and bounded group identifiers in that order;
     * decoding rejects counts outside the protocol limit before allocating the list.
     */
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

    /**
     * Normalizes the scan token and selected identifiers and validates their count, length, and uniqueness.
     *
     * @param scanToken server-issued scan token; {@code null} is normalized to empty text before validation
     * @param selectedGroupIds group identifiers to preview; the container and its elements may be {@code null} and are
     *     normalized before validation
     * @throws IllegalArgumentException if the token is empty or oversized, or if the normalized selection is empty, too
     *     large, contains an empty or oversized identifier, or contains duplicates
     */
    public OreImportPreviewRequestPayload(
            @Nullable String scanToken, @Nullable List<@Nullable String> selectedGroupIds) {
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
        this.scanToken = scanToken;
        this.selectedGroupIds = selectedGroupIds;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
