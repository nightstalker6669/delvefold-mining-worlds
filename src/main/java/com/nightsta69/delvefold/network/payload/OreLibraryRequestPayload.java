package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.network.ProtocolLimits;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

/**
 * Serverbound request for one filtered page of the server-authoritative Unified Ores library.
 *
 * @param expectedOreRevision active ore revision shown by the administration snapshot
 * @param catalogToken blank to start discovery, otherwise the retained player-bound catalog token
 * @param page requested zero-based filtered page
 * @param query bounded material, provider, or exact-block search query
 * @param showConfigured whether configured logical families should remain visible
 */
public record OreLibraryRequestPayload(
        long expectedOreRevision, String catalogToken, int page, String query, boolean showConfigured)
        implements CustomPacketPayload {
    /** NeoForge payload type for library-page requests. */
    public static final Type<OreLibraryRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "ore_library_request"));

    /** Wire codec for page, bounded query, and configured-family visibility. */
    public static final StreamCodec<RegistryFriendlyByteBuf, OreLibraryRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarLong(payload.expectedOreRevision());
                buffer.writeUtf(payload.catalogToken(), ProtocolLimits.MAX_IMPORT_TOKEN_LENGTH);
                buffer.writeVarInt(payload.page());
                buffer.writeUtf(payload.query(), ProtocolLimits.MAX_ORE_LIBRARY_QUERY_LENGTH);
                buffer.writeBoolean(payload.showConfigured());
            },
            buffer -> new OreLibraryRequestPayload(
                    buffer.readVarLong(),
                    buffer.readUtf(ProtocolLimits.MAX_IMPORT_TOKEN_LENGTH),
                    buffer.readVarInt(),
                    buffer.readUtf(ProtocolLimits.MAX_ORE_LIBRARY_QUERY_LENGTH),
                    buffer.readBoolean()));

    /** Normalizes and bounds untrusted page and query fields before transmission or handling. */
    public OreLibraryRequestPayload(
            long expectedOreRevision,
            @Nullable String catalogToken,
            int page,
            @Nullable String query,
            boolean showConfigured) {
        if (expectedOreRevision < 0L) {
            throw new IllegalArgumentException("Ore revision cannot be negative");
        }
        String normalizedToken = catalogToken == null ? "" : catalogToken.trim();
        if (normalizedToken.length() > ProtocolLimits.MAX_IMPORT_TOKEN_LENGTH) {
            throw new IllegalArgumentException("Invalid ore library catalog token");
        }
        if (page < 0 || page > ProtocolLimits.MAX_IMPORT_GROUPS) {
            throw new IllegalArgumentException("Invalid ore library page: " + page);
        }
        String normalized = query == null ? "" : query.trim();
        if (normalized.length() > ProtocolLimits.MAX_ORE_LIBRARY_QUERY_LENGTH
                || normalized.codePointCount(0, normalized.length()) > ProtocolLimits.MAX_ORE_LIBRARY_QUERY_LENGTH) {
            throw new IllegalArgumentException("Ore library query is too long");
        }
        this.expectedOreRevision = expectedOreRevision;
        this.catalogToken = normalizedToken;
        this.page = page;
        this.query = normalized;
        this.showConfigured = showConfigured;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
