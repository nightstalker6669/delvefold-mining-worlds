package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.network.ProtocolLimits;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

/**
 * Serverbound request for another page of an existing ore-import preview.
 *
 * <p>The server requires configure permission and validates that the bounded commit token belongs to the requesting
 * player and still matches the current import-session binding.
 *
 * @param commitToken normalized server-issued preview token
 * @param page zero-based preview page, at most {@link ProtocolLimits#MAX_IMPORT_GROUPS}
 */
public record OreImportPreviewPageRequestPayload(String commitToken, int page) implements CustomPacketPayload {
    /** NeoForge payload type for the serverbound ore-import preview page request. */
    public static final Type<OreImportPreviewPageRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "ore_import_preview_page"));

    /** Wire codec encoding the bounded commit token before the variable-length page index. */
    public static final StreamCodec<RegistryFriendlyByteBuf, OreImportPreviewPageRequestPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeUtf(payload.commitToken(), ProtocolLimits.MAX_IMPORT_TOKEN_LENGTH);
                        buffer.writeVarInt(payload.page());
                    },
                    buffer -> new OreImportPreviewPageRequestPayload(
                            buffer.readUtf(ProtocolLimits.MAX_IMPORT_TOKEN_LENGTH), buffer.readVarInt()));

    /**
     * Normalizes the commit token and validates the page against coarse protocol bounds.
     *
     * @param commitToken server-issued preview token; {@code null} is normalized to empty text before validation
     * @param page zero-based requested page
     * @throws IllegalArgumentException if the token is empty or oversized, or the page is outside the protocol range
     */
    public OreImportPreviewPageRequestPayload(@Nullable String commitToken, int page) {
        commitToken = commitToken == null ? "" : commitToken.trim();
        if (commitToken.isEmpty() || commitToken.length() > ProtocolLimits.MAX_IMPORT_TOKEN_LENGTH)
            throw new IllegalArgumentException("Invalid preview token");
        if (page < 0 || page > ProtocolLimits.MAX_IMPORT_GROUPS)
            throw new IllegalArgumentException("Invalid preview page");
        this.commitToken = commitToken;
        this.page = page;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
