package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.network.ProtocolLimits;
import com.nightsta69.delvefold.network.codec.DelvefoldStreamCodecs;
import com.nightsta69.delvefold.network.model.BackupOperation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Serverbound request to restore, retain, verify, or delete a world backup.
 *
 * <p>The server requires world-management permission and remains responsible for validating the backup identifier and
 * applying revision and operation-specific safety checks.
 *
 * @param expectedSettingsRevision settings revision observed when the request was composed
 * @param operation requested backup operation
 * @param backupId bounded server-issued backup identifier, when the operation targets a backup
 */
public record BackupActionPayload(long expectedSettingsRevision, BackupOperation operation, String backupId)
        implements CustomPacketPayload {
    /** NeoForge payload type for the serverbound backup operation request. */
    public static final Type<BackupActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "backup_action"));

    /** Wire codec encoding settings revision, operation, and bounded backup identifier in that order. */
    public static final StreamCodec<RegistryFriendlyByteBuf, BackupActionPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeLong(payload.expectedSettingsRevision());
                DelvefoldStreamCodecs.writeEnum(buffer, payload.operation());
                DelvefoldStreamCodecs.writeString(buffer, payload.backupId(), ProtocolLimits.SHORT_TEXT_LENGTH);
            },
            buffer -> new BackupActionPayload(
                    buffer.readLong(),
                    DelvefoldStreamCodecs.readEnum(buffer, BackupOperation.class),
                    DelvefoldStreamCodecs.readString(buffer, ProtocolLimits.SHORT_TEXT_LENGTH)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
