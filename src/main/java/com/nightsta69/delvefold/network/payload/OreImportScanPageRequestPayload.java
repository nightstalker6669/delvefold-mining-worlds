package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.network.ProtocolLimits;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

/**
 * Serverbound request for another page of a player's existing ore-import scan.
 *
 * <p>The server requires configure permission and validates that the bounded scan token belongs to the requesting
 * player and still matches the current import-session binding.
 *
 * @param scanToken normalized server-issued scan token
 * @param page zero-based scan page, at most {@link ProtocolLimits#MAX_IMPORT_GROUPS}
 */
public record OreImportScanPageRequestPayload(String scanToken, int page) implements CustomPacketPayload {
    /** NeoForge payload type for the serverbound ore-import scan page request. */
    public static final Type<OreImportScanPageRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "ore_import_scan_page"));

    /** Wire codec encoding the bounded scan token before the variable-length page index. */
    public static final StreamCodec<RegistryFriendlyByteBuf, OreImportScanPageRequestPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeUtf(payload.scanToken(), ProtocolLimits.MAX_IMPORT_TOKEN_LENGTH);
                        buffer.writeVarInt(payload.page());
                    },
                    buffer -> new OreImportScanPageRequestPayload(
                            buffer.readUtf(ProtocolLimits.MAX_IMPORT_TOKEN_LENGTH), buffer.readVarInt()));

    /**
     * Normalizes the scan token and validates the page against coarse protocol bounds.
     *
     * @param scanToken server-issued scan token; {@code null} is normalized to empty text before validation
     * @param page zero-based requested page
     * @throws IllegalArgumentException if the token is empty or oversized, or the page is outside the protocol range
     */
    public OreImportScanPageRequestPayload(@Nullable String scanToken, int page) {
        scanToken = requiredToken(scanToken);
        if (page < 0 || page > ProtocolLimits.MAX_IMPORT_GROUPS)
            throw new IllegalArgumentException("Invalid scan page");
        this.scanToken = scanToken;
        this.page = page;
    }

    private static String requiredToken(@Nullable String value) {
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
