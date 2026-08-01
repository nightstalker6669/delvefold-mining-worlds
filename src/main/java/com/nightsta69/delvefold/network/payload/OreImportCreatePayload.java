package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.network.ProtocolLimits;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

/**
 * Serverbound request to commit a previously previewed ore import as a profile.
 *
 * <p>The server requires configure permission and consumes the player-bound commit token only after rechecking its
 * session binding, target profile, and authoritative ore revision.
 *
 * @param commitToken normalized server-issued preview token, bounded by {@link ProtocolLimits#MAX_IMPORT_TOKEN_LENGTH}
 * @param targetProfileId normalized identifier for the profile to create
 */
public record OreImportCreatePayload(String commitToken, String targetProfileId) implements CustomPacketPayload {
    /** NeoForge payload type for the serverbound ore-import commit request. */
    public static final Type<OreImportCreatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "ore_import_create"));

    /** Wire codec encoding the bounded commit token before the bounded target profile identifier. */
    public static final StreamCodec<RegistryFriendlyByteBuf, OreImportCreatePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUtf(payload.commitToken(), ProtocolLimits.MAX_IMPORT_TOKEN_LENGTH);
                buffer.writeUtf(payload.targetProfileId(), ProtocolLimits.ID_LENGTH);
            },
            buffer -> new OreImportCreatePayload(
                    buffer.readUtf(ProtocolLimits.MAX_IMPORT_TOKEN_LENGTH), buffer.readUtf(ProtocolLimits.ID_LENGTH)));

    /**
     * Normalizes and validates the commit token and target profile identifier.
     *
     * @param commitToken server-issued preview token; {@code null} is normalized to empty text before validation
     * @param targetProfileId profile identifier to create; {@code null} is normalized to empty text before validation
     * @throws IllegalArgumentException if either normalized value is empty or exceeds its protocol bound
     */
    public OreImportCreatePayload(@Nullable String commitToken, @Nullable String targetProfileId) {
        commitToken = commitToken == null ? "" : commitToken.trim();
        targetProfileId = targetProfileId == null ? "" : targetProfileId.trim();
        if (commitToken.isEmpty() || commitToken.length() > ProtocolLimits.MAX_IMPORT_TOKEN_LENGTH)
            throw new IllegalArgumentException("Invalid preview token");
        if (targetProfileId.isEmpty() || targetProfileId.length() > ProtocolLimits.ID_LENGTH)
            throw new IllegalArgumentException("Invalid profile ID");
        this.commitToken = commitToken;
        this.targetProfileId = targetProfileId;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
