package com.nightsta69.delvefold.admin;

import com.nightsta69.delvefold.audit.AsyncAuditMutationTracker;
import com.nightsta69.delvefold.audit.AuditMutation;
import com.nightsta69.delvefold.audit.DelvefoldAuditService;
import com.nightsta69.delvefold.config.DelvefoldConfigService;
import com.nightsta69.delvefold.network.DelvefoldNetwork;
import com.nightsta69.delvefold.network.model.ActionStatus;
import com.nightsta69.delvefold.network.model.BackupOperation;
import com.nightsta69.delvefold.network.service.DelvefoldAdminService.ServiceResult;
import com.nightsta69.delvefold.reset.BackupCatalogCache;
import com.nightsta69.delvefold.reset.BackupDeletionGuard;
import com.nightsta69.delvefold.reset.BackupVerificationResult;
import com.nightsta69.delvefold.reset.BackupVerificationService;
import com.nightsta69.delvefold.reset.WorldBackupCatalog;
import com.nightsta69.delvefold.reset.WorldOperationPreview;
import com.nightsta69.delvefold.reset.WorldOperationResult;
import com.nightsta69.delvefold.reset.WorldRestoreService;
import java.io.IOException;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/** Executes authorized, revision-checked backup catalog and restore operations for the administration facade. */
final class AdminBackupOperations {
    private AdminBackupOperations() {}

    static ServiceResult perform(
            ServerPlayer player,
            long expectedSettingsRevision,
            BackupOperation operation,
            String backupId,
            System.Logger logger) {
        try {
            var server = player.getServer();
            var saveRoot = server.getWorldPath(LevelResource.ROOT);
            BackupCatalogCache backupCache = BackupCatalogCache.get();
            BackupCatalogCache.Snapshot cachedCatalog = backupCache.snapshot(saveRoot);
            if (operation == BackupOperation.RESTORE) {
                WorldBackupCatalog.@Nullable BackupSummary summary = summary(cachedCatalog, backupId);
                if (summary == null) {
                    return rejected(
                            expectedSettingsRevision,
                            cachedCatalog.refreshing()
                                    ? message("message.delvefold.admin.backup.catalog_refreshing")
                                    : message("message.delvefold.admin.backup.unknown", backupId));
                }
                WorldOperationPreview preview = WorldRestoreService.get()
                        .requestCached(server, backupId, player.getGameProfile().getName(), summary);
                if (!preview.accepted()) {
                    return rejected(expectedSettingsRevision, preview.message());
                }
                WorldOperationResult confirmed = WorldRestoreService.get().confirm(server, preview.confirmationToken());
                if (confirmed.success()) {
                    audit(
                            player,
                            AuditMutation.Operation.BACKUP_RESTORE_ACCEPTED,
                            AuditMutation.ObjectType.BACKUP,
                            backupId,
                            -1L,
                            -1L);
                    var unusedRefreshAfterRestore = backupCache.invalidateAndRefresh(saveRoot);
                }
                return new ServiceResult(
                        confirmed.success() ? ActionStatus.ACCEPTED : ActionStatus.ERROR,
                        expectedSettingsRevision,
                        confirmed.message(),
                        true);
            }
            if (operation == BackupOperation.CANCEL_RESTORE) {
                String selectedBackupId = WorldRestoreService.get().selectedBackupId(server);
                WorldOperationResult cancelled = WorldRestoreService.get().cancel(server);
                if (cancelled.success()) {
                    var unusedRefreshAfterCancellation = backupCache.invalidateAndRefresh(saveRoot);
                    audit(
                            player,
                            AuditMutation.Operation.BACKUP_RESTORE_CANCELLED,
                            AuditMutation.ObjectType.BACKUP,
                            selectedBackupId,
                            -1L,
                            -1L);
                }
                return new ServiceResult(
                        cancelled.success() ? ActionStatus.ACCEPTED : ActionStatus.REJECTED,
                        expectedSettingsRevision,
                        cancelled.message(),
                        true);
            }
            if (operation == BackupOperation.VERIFY) {
                WorldBackupCatalog.@Nullable BackupSummary summary = summary(cachedCatalog, backupId);
                if (summary == null) {
                    return rejected(
                            expectedSettingsRevision,
                            cachedCatalog.refreshing()
                                    ? message("message.delvefold.admin.backup.catalog_refreshing")
                                    : message("message.delvefold.admin.backup.unknown", backupId));
                }
                var playerId = player.getUUID();
                var auditActor = player.getGameProfile().getName();
                BackupVerificationService verification = BackupVerificationService.forSave(saveRoot);
                if (verification.isInFlight(backupId)) {
                    return accepted(
                            expectedSettingsRevision,
                            message("message.delvefold.admin.backup.verification_running"),
                            false);
                }
                var future = summary.legacy()
                        ? AsyncAuditMutationTracker.get()
                                .startTracked(
                                        saveRoot,
                                        () -> verification.validateLegacyAndCreateManifestAsync(backupId),
                                        (result, failure) -> {
                                            if (failure == null
                                                    && result.status()
                                                            == BackupVerificationResult.Status.LEGACY_UPGRADED) {
                                                audit(
                                                        auditActor,
                                                        AuditMutation.Operation.BACKUP_MANIFEST_CREATED,
                                                        AuditMutation.ObjectType.BACKUP,
                                                        backupId,
                                                        -1L,
                                                        -1L);
                                            }
                                        })
                        : verification.verifyAsync(backupId);
                var unusedVerificationCompletion = future.whenComplete((result, failure) -> server.execute(() -> {
                    var unusedRefreshAfterVerification = backupCache.invalidateAndRefresh(saveRoot);
                    if (failure != null) {
                        logger.log(
                                System.Logger.Level.ERROR,
                                "Backup verification worker failed for " + backupId,
                                failure);
                    }
                    ServerPlayer online = server.getPlayerList().getPlayer(playerId);
                    if (online == null) {
                        return;
                    }
                    long currentRevision;
                    try {
                        currentRevision = DelvefoldConfigService.get()
                                .snapshot()
                                .settings()
                                .revision();
                    } catch (IllegalStateException ignored) {
                        currentRevision = expectedSettingsRevision;
                    }
                    boolean successful = failure == null && result.successful();
                    String resultMessage = failure == null
                            ? result.message()
                            : message("message.delvefold.backup_verification.internal_error", backupId);
                    DelvefoldNetwork.sendAsyncResult(
                            online,
                            new ServiceResult(
                                    successful ? ActionStatus.ACCEPTED : ActionStatus.ERROR,
                                    currentRevision,
                                    resultMessage,
                                    true));
                }));
                return accepted(
                        expectedSettingsRevision,
                        summary.legacy()
                                ? message("message.delvefold.admin.backup.legacy_validation_started")
                                : message("message.delvefold.admin.backup.verification_started"),
                        false);
            }
            if (operation == BackupOperation.DELETE) {
                var playerId = player.getUUID();
                var auditActor = player.getGameProfile().getName();
                var deletion = AsyncAuditMutationTracker.get()
                        .startTracked(
                                saveRoot,
                                () -> backupCache.deleteAndRefreshAsync(server, backupId),
                                (deleted, failure) -> {
                                    if (failure == null && Boolean.TRUE.equals(deleted)) {
                                        audit(
                                                auditActor,
                                                AuditMutation.Operation.BACKUP_DELETED,
                                                AuditMutation.ObjectType.BACKUP,
                                                backupId,
                                                -1L,
                                                -1L);
                                    }
                                });
                var unusedDeletionCompletion = deletion.whenComplete((deleted, failure) -> server.execute(() -> {
                    boolean success = failure == null && Boolean.TRUE.equals(deleted);
                    if (failure != null) {
                        logger.log(System.Logger.Level.ERROR, "Could not delete Delvefold backup " + backupId, failure);
                    }
                    ServerPlayer online = server.getPlayerList().getPlayer(playerId);
                    if (online != null) {
                        String message = success
                                ? message("message.delvefold.admin.backup.deleted", backupId)
                                : backupDeleteFailure(backupId, failure);
                        DelvefoldNetwork.sendAsyncResult(
                                online,
                                new ServiceResult(
                                        success ? ActionStatus.ACCEPTED : ActionStatus.ERROR,
                                        expectedSettingsRevision,
                                        message,
                                        true));
                    }
                }));
                return accepted(
                        expectedSettingsRevision, message("message.delvefold.admin.backup.delete_started"), false);
            }
            WorldBackupCatalog catalog = new WorldBackupCatalog(saveRoot);
            return switch (operation) {
                case PIN -> {
                    boolean changed = catalog.setPinned(backupId, true);
                    var unusedRefreshAfterPin = backupCache.invalidateAndRefresh(saveRoot);
                    if (changed) {
                        audit(
                                player,
                                AuditMutation.Operation.BACKUP_PINNED,
                                AuditMutation.ObjectType.BACKUP,
                                backupId,
                                -1L,
                                -1L);
                    }
                    yield accepted(
                            expectedSettingsRevision, message("message.delvefold.admin.backup.pinned", backupId), true);
                }
                case UNPIN -> {
                    boolean changed = catalog.setPinned(backupId, false);
                    var unusedRefreshAfterUnpin = backupCache.invalidateAndRefresh(saveRoot);
                    if (changed) {
                        audit(
                                player,
                                AuditMutation.Operation.BACKUP_UNPINNED,
                                AuditMutation.ObjectType.BACKUP,
                                backupId,
                                -1L,
                                -1L);
                    }
                    yield accepted(
                            expectedSettingsRevision,
                            message("message.delvefold.admin.backup.unpinned", backupId),
                            true);
                }
                case RESTORE, DELETE, VERIFY, CANCEL_RESTORE -> throw new IllegalStateException("Handled above");
            };
        } catch (IOException | IllegalArgumentException exception) {
            return new ServiceResult(
                    ActionStatus.ERROR,
                    expectedSettingsRevision,
                    message("message.delvefold.admin.backup.failed", exception.getMessage()),
                    false);
        }
    }

    static WorldBackupCatalog.@Nullable BackupSummary summary(
            BackupCatalogCache.Snapshot catalog, @Nullable String backupId) {
        return catalog.backups().stream()
                .filter(candidate -> candidate.id().equals(backupId))
                .findFirst()
                .orElse(null);
    }

    static String backupDeleteFailure(String backupId, @Nullable Throwable failure) {
        BackupDeletionGuard.@Nullable DeletionRejectedException rejection = deletionRejection(failure);
        if (rejection == null) {
            return message("message.delvefold.admin.backup.delete_failed", backupId);
        }
        String key =
                switch (rejection.reason()) {
                    case REFERENCED -> "message.delvefold.backup_delete.referenced";
                    case IN_PROGRESS -> "message.delvefold.backup_delete.in_progress";
                    case SESSION_CLOSED -> "message.delvefold.backup_delete.session_closed";
                };
        return message(key, backupId);
    }

    // Throwable cause cycles are identity cycles; value equality may recurse or conflate distinct causes.
    @SuppressWarnings("ReferenceEquality")
    private static BackupDeletionGuard.@Nullable DeletionRejectedException deletionRejection(
            @Nullable Throwable failure) {
        @Nullable Throwable current = failure;
        while (current != null) {
            if (current instanceof BackupDeletionGuard.DeletionRejectedException rejection) {
                return rejection;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return null;
    }

    private static void audit(
            ServerPlayer player,
            AuditMutation.Operation operation,
            AuditMutation.ObjectType objectType,
            String objectId,
            long oldRevision,
            long newRevision) {
        audit(player.getGameProfile().getName(), operation, objectType, objectId, oldRevision, newRevision);
    }

    private static void audit(
            String actor,
            AuditMutation.Operation operation,
            AuditMutation.ObjectType objectType,
            String objectId,
            long oldRevision,
            long newRevision) {
        DelvefoldAuditService.get()
                .record(new AuditMutation(actor, operation, objectType, objectId, oldRevision, newRevision));
    }

    private static ServiceResult accepted(long revision, String message, boolean refresh) {
        return new ServiceResult(ActionStatus.ACCEPTED, revision, message, refresh);
    }

    private static ServiceResult rejected(long revision, String message) {
        return new ServiceResult(ActionStatus.REJECTED, revision, message, false);
    }

    private static String message(String translationKey, @Nullable Object... arguments) {
        return AdminLocalizedMessage.encode(translationKey, arguments);
    }
}
