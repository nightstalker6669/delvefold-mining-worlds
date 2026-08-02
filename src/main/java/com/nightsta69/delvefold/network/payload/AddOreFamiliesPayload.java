package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.network.ProtocolLimits;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

/**
 * Serverbound atomic request to add default rules for selected logical ore families.
 *
 * @param expectedRevision ore revision displayed when the exact catalog was opened
 * @param catalogToken opaque player-bound token identifying that retained catalog
 * @param familyIds distinct server-issued material-family identifiers
 */
public record AddOreFamiliesPayload(long expectedRevision, String catalogToken, List<String> familyIds)
        implements CustomPacketPayload {
    /** NeoForge payload type for atomic family additions. */
    public static final Type<AddOreFamiliesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "add_ore_families"));

    /** Strict codec that rejects invalid counts before allocating the identifier list. */
    public static final StreamCodec<RegistryFriendlyByteBuf, AddOreFamiliesPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarLong(payload.expectedRevision());
                buffer.writeUtf(payload.catalogToken(), ProtocolLimits.MAX_IMPORT_TOKEN_LENGTH);
                buffer.writeVarInt(payload.familyIds().size());
                for (String id : payload.familyIds()) {
                    buffer.writeUtf(id, ProtocolLimits.ID_LENGTH);
                }
            },
            buffer -> {
                long revision = buffer.readVarLong();
                String catalogToken = buffer.readUtf(ProtocolLimits.MAX_IMPORT_TOKEN_LENGTH);
                int count = buffer.readVarInt();
                if (count < 1 || count > ProtocolLimits.MAX_ORE_LIBRARY_SELECTIONS) {
                    throw new IllegalArgumentException("Invalid Unified Ores selection count: " + count);
                }
                java.util.ArrayList<String> ids = new java.util.ArrayList<>(count);
                for (int index = 0; index < count; index++) {
                    ids.add(buffer.readUtf(ProtocolLimits.ID_LENGTH));
                }
                return new AddOreFamiliesPayload(revision, catalogToken, ids);
            });

    /** Normalizes, bounds, and requires distinct canonical server-issued family IDs. */
    public AddOreFamiliesPayload(
            long expectedRevision, @Nullable String catalogToken, @Nullable List<@Nullable String> familyIds) {
        if (expectedRevision < 0L) {
            throw new IllegalArgumentException("Ore revision cannot be negative");
        }
        String normalizedToken = catalogToken == null ? "" : catalogToken.trim();
        if (normalizedToken.isEmpty() || normalizedToken.length() > ProtocolLimits.MAX_IMPORT_TOKEN_LENGTH) {
            throw new IllegalArgumentException("Invalid ore library catalog token");
        }
        List<String> normalized = OreFamilySelection.normalize(familyIds);
        this.expectedRevision = expectedRevision;
        this.catalogToken = normalizedToken;
        this.familyIds = normalized;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
