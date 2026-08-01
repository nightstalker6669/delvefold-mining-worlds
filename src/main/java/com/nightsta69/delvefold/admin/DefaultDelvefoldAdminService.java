package com.nightsta69.delvefold.admin;

import com.nightsta69.delvefold.audit.AsyncAuditMutationTracker;
import com.nightsta69.delvefold.audit.AuditMutation;
import com.nightsta69.delvefold.audit.DelvefoldAuditService;
import com.nightsta69.delvefold.config.AdminAccess;
import com.nightsta69.delvefold.config.ConfigLoadResult;
import com.nightsta69.delvefold.config.ConfigSnapshot;
import com.nightsta69.delvefold.config.ConfigWriteResult;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast;
import com.nightsta69.delvefold.config.DelvefoldConfigService;
import com.nightsta69.delvefold.config.model.BiomeFilter;
import com.nightsta69.delvefold.config.model.GameplaySettings;
import com.nightsta69.delvefold.config.model.GeologyTheme;
import com.nightsta69.delvefold.config.model.HeightDistribution;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.OreRule;
import com.nightsta69.delvefold.config.model.OreTarget;
import com.nightsta69.delvefold.config.model.PortalSettings;
import com.nightsta69.delvefold.config.model.SpawnBand;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.validation.ConfigIssue;
import com.nightsta69.delvefold.config.validation.ConfigIssueMessages;
import com.nightsta69.delvefold.config.validation.ValidationReport;
import com.nightsta69.delvefold.diagnostics.DelvefoldDoctorService;
import com.nightsta69.delvefold.network.model.ActionStatus;
import com.nightsta69.delvefold.network.model.AdminOperation;
import com.nightsta69.delvefold.network.model.AdminSnapshot;
import com.nightsta69.delvefold.network.model.BackupOperation;
import com.nightsta69.delvefold.network.model.ProfileOperation;
import com.nightsta69.delvefold.network.ProtocolLimits;
import com.nightsta69.delvefold.network.DelvefoldNetwork;
import com.nightsta69.delvefold.network.service.DelvefoldAdminService;
import com.nightsta69.delvefold.reset.BackupMode;
import com.nightsta69.delvefold.reset.BackupCatalogCache;
import com.nightsta69.delvefold.reset.BackupDeletionGuard;
import com.nightsta69.delvefold.reset.BackupVerificationResult;
import com.nightsta69.delvefold.reset.BackupVerificationService;
import com.nightsta69.delvefold.reset.WorldOperationPreview;
import com.nightsta69.delvefold.reset.WorldOperationRequest;
import com.nightsta69.delvefold.reset.WorldOperationResult;
import com.nightsta69.delvefold.reset.WorldOperationService;
import com.nightsta69.delvefold.reset.WorldBackupCatalog;
import com.nightsta69.delvefold.reset.WorldRestoreService;
import com.nightsta69.delvefold.world.landmark.catalog.LandmarkCatalogService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/** Connects the bounded GUI protocol to the same config/reset services used by commands. */
public final class DefaultDelvefoldAdminService implements DelvefoldAdminService {
    private static final System.Logger LOGGER = System.getLogger(DefaultDelvefoldAdminService.class.getName());

    @Override
    public AdminSnapshot snapshot(ServerPlayer player, int requestedOrePage, int requestedOrePageSize) {
        requireConfigure(player);
        ConfigSnapshot snapshot = DelvefoldConfigService.get().snapshot();
        boolean compatible = !DelvefoldConfigService.get().isReadOnlyIncompatible();
        var settings = snapshot.settings();
        var saveRoot = player.getServer().getWorldPath(LevelResource.ROOT);
        BackupCatalogCache.Snapshot backupCatalog = BackupCatalogCache.get().snapshot(saveRoot);
        List<String> diagnostics = new ArrayList<>();
        diagnostics.add(message("message.delvefold.admin.diagnostic.config_hash", snapshot.diskHash()));
        diagnostics.add(message("message.delvefold.admin.diagnostic.loaded", snapshot.loadedAt()));
        diagnostics.add(message("message.delvefold.admin.diagnostic.generation_epoch",
                settings.generationEpoch()));
        var landmarkCatalog = LandmarkCatalogService.get();
        var landmarkDiagnostics = landmarkCatalog.diagnostics();
        diagnostics.add(message(landmarkDiagnostics.lastReloadAccepted()
                        ? "message.delvefold.admin.diagnostic.landmarks.applied"
                        : "message.delvefold.admin.diagnostic.landmarks.rejected",
                landmarkCatalog.snapshot().revision(), landmarkCatalog.snapshot().definitions().size()));
        landmarkDiagnostics.errors().stream().limit(8)
                .forEach(error -> diagnostics.add(message(
                        "message.delvefold.admin.diagnostic.landmarks.error", error)));
        if (!compatible) {
            diagnostics.add(DelvefoldConfigService.get().compatibilityMessage());
        }
        if (backupCatalog.refreshing()) {
            diagnostics.add(message("message.delvefold.admin.diagnostic.backups.refreshing"));
        }
        if (!backupCatalog.lastError().isBlank()) {
            diagnostics.add(message("message.delvefold.admin.diagnostic.backups.error",
                    backupCatalog.lastError()));
        }
        for (ConfigIssue issue : snapshot.validation().issues()) {
            diagnostics.add(ConfigIssueMessages.encode(issue));
        }
        List<String> auxiliaryDiagnostics = List.copyOf(diagnostics);
        diagnostics.clear();
        try {
            diagnostics.addAll(DelvefoldDoctorService.get().cachedRenderedLines(player.getServer()).stream()
                    .limit(ProtocolLimits.MAX_DIAGNOSTICS)
                    .toList());
        } catch (RuntimeException exception) {
            diagnostics.add(message("message.delvefold.admin.diagnostic.doctor_unavailable",
                    exception.getMessage()));
        }
        for (String diagnostic : auxiliaryDiagnostics) {
            if (diagnostics.size() >= ProtocolLimits.MAX_DIAGNOSTICS) {
                break;
            }
            diagnostics.add(diagnostic);
        }
        String portalStatus;
        if (!settings.initialized()) {
            portalStatus = message("message.delvefold.admin.portal.uninitialized");
        } else if (WorldOperationService.get().isEntryBlocked()) {
            portalStatus = message("message.delvefold.admin.portal.blocked");
        } else if (!settings.portal().enabled()) {
            portalStatus = message("message.delvefold.admin.portal.disabled");
        } else {
            portalStatus = settings.portal().routingMode()
                    == com.nightsta69.delvefold.config.model.PortalRoutingMode.CENTRAL_HUB
                    ? message("message.delvefold.admin.portal.ready.central_hub",
                            settings.terrainMode().serializedName(), settings.portal().hub().x(),
                            settings.portal().hub().z(), settings.portal().hub().protectionRadius(),
                            settings.portal().cooldownSeconds())
                    : message("message.delvefold.admin.portal.ready.coordinate_linked",
                            settings.terrainMode().serializedName(), settings.portal().cooldownSeconds());
        }
        String worldStatus = settings.initialized()
                ? message("message.delvefold.admin.world.ready",
                        settings.terrainMode().serializedName(), settings.generationEpoch())
                : message("message.delvefold.admin.world.uninitialized");
        int pageSize = Math.max(1, Math.min(requestedOrePageSize, ProtocolLimits.MAX_ORE_RULES_PER_PAGE));
        int totalRules = snapshot.ores().rules().size();
        int maximumPage = Math.max(0, (totalRules - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedOrePage, maximumPage));
        int start = Math.min(totalRules, page * pageSize);
        int end = Math.min(totalRules, start + pageSize);
        List<AdminSnapshot.OreRuleDraft> pageRules = snapshot.ores().rules().subList(start, end).stream()
                .map(DefaultDelvefoldAdminService::toDraft)
                .toList();
        List<AdminSnapshot.ProfileDraft> profiles;
        try {
            profiles = DelvefoldConfigService.get().listProfiles().stream()
                    .map(profile -> new AdminSnapshot.ProfileDraft(profile.id(), profile.builtIn(),
                            profile.localOverride(), profile.ruleCount(), profile.revision(),
                            profile.issues().stream().noneMatch(issue -> issue.severity()
                                    == com.nightsta69.delvefold.config.validation.IssueSeverity.ERROR)))
                    .toList();
        } catch (IOException exception) {
            diagnostics.add(message("message.delvefold.admin.diagnostic.profiles.error",
                    exception.getMessage()));
            profiles = List.of();
        }
        List<AdminSnapshot.BackupDraft> backups = backupCatalog.backups().stream()
                .limit(ProtocolLimits.MAX_BACKUPS)
                .map(backup -> new AdminSnapshot.BackupDraft(backup.id(), backup.createdAtEpochMillis(),
                        backup.operation(), backup.terrain(), backup.sizeBytes(), backup.pinned(),
                        backup.restorable(), backup.valid(), backup.manifestPresent(),
                        backup.verified(), backup.legacy()))
                .toList();
        return new AdminSnapshot(
                snapshot.ores().revision(),
                settings.revision(),
                compatible,
                settings.initialized(),
                settings.terrainMode(),
                settings.orePreset(),
                settings.gameplay(),
                settings.portal(),
                settings.identity(),
                new AdminSnapshot.AdminCapabilities(
                        AdminAccess.canConfigure(player),
                        compatible && AdminAccess.canConfigure(player),
                        compatible && AdminAccess.canManageWorld(player),
                        compatible && AdminAccess.canManageWorld(player),
                        AdminAccess.canConfigure(player)),
                snapshot.ores().profile(),
                profiles,
                backups,
                portalStatus,
                worldStatus,
                AdminSnapshot.PendingOperation.resolve(
                        WorldOperationService.get().hasPending(player.getServer()),
                        WorldRestoreService.get().hasPending(player.getServer())),
                diagnostics,
                totalRules,
                page,
                pageRules
        );
    }

    @Override
    public OreProfileForecast forecast(ServerPlayer player, String profileId, int page) {
        requireConfigure(player);
        try {
            return DelvefoldConfigService.get().forecast(
                    profileId, page, OreProfileForecast.DEFAULT_RULES_PER_PAGE);
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Could not forecast profile: " + exception.getMessage(), exception);
        }
    }

    @Override
    public ServiceResult initialize(
            ServerPlayer player,
            long expectedOreRevision,
            long expectedSettingsRevision,
            TerrainMode terrainMode,
            OrePreset orePreset,
            GameplaySettings gameplay,
            com.nightsta69.delvefold.config.model.WorldIdentitySettings identity
    ) {
        requireConfigure(player);
        ConfigWriteResult initialized = DelvefoldConfigService.get().initialize(
                expectedOreRevision,
                expectedSettingsRevision,
                terrainMode,
                orePreset,
                gameplay.preset(),
                identity
        );
        if (!initialized.saved()) {
            return fromWrite(initialized, message("message.delvefold.admin.initialize.rejected"));
        }
        ConfigSnapshot after = DelvefoldConfigService.get().snapshot();
        if (!after.settings().gameplay().equals(gameplay)) {
            ConfigWriteResult customized = DelvefoldConfigService.get().updateSettings(
                    after.settings().revision(),
                    settings -> settings.withGameplay(gameplay)
            );
            if (!customized.saved()) {
                return fromWrite(customized,
                        message("message.delvefold.admin.initialize.gameplay_rejected"));
            }
        }
        return accepted(DelvefoldConfigService.get().snapshot().settings().revision(),
                message("message.delvefold.admin.initialize.accepted"), true);
    }

    @Override
    public ServiceResult saveOreRule(
            ServerPlayer player,
            long expectedRevision,
            AdminSnapshot.OreRuleDraft draft,
            boolean createOnly) {
        requireConfigure(player);
        OreRule replacement = fromDraft(draft);
        ConfigWriteResult result = DelvefoldConfigService.get().saveOreRule(
                expectedRevision, replacement, createOnly);
        return fromWrite(result, message("message.delvefold.admin.ore.saved", replacement.id()));
    }

    @Override
    public ServiceResult deleteOreRule(ServerPlayer player, long expectedRevision, String ruleId) {
        requireConfigure(player);
        ConfigSnapshot before = DelvefoldConfigService.get().snapshot();
        if (before.ores().rules().stream().noneMatch(rule -> rule.id().equals(ruleId))) {
            return rejected(expectedRevision, message("message.delvefold.admin.ore.unknown", ruleId));
        }
        ConfigWriteResult result = DelvefoldConfigService.get().updateOres(expectedRevision, document ->
                document.nextRevision(document.rules().stream().filter(rule -> !rule.id().equals(ruleId)).toList(),
                        document.profile()));
        return fromWrite(result, message("message.delvefold.admin.ore.deleted", ruleId));
    }

    @Override
    public ServiceResult updateGameplay(ServerPlayer player, long expectedRevision, GameplaySettings gameplay) {
        requireConfigure(player);
        ConfigWriteResult result = DelvefoldConfigService.get().updateSettings(
                expectedRevision,
                settings -> settings.withGameplay(gameplay)
        );
        return fromWrite(result, message("message.delvefold.admin.gameplay.saved"));
    }

    @Override
    public ServiceResult updatePortal(ServerPlayer player, long expectedRevision, PortalSettings portal) {
        requireConfigure(player);
        ConfigSnapshot before = DelvefoldConfigService.get().snapshot();
        if (!before.settings().portal().hub().equals(portal.hub())
                && !AdminAccess.canManageWorld(player)) {
            return rejected(expectedRevision, message("message.delvefold.admin.portal.hub_permission"));
        }
        ConfigWriteResult result = DelvefoldConfigService.get().updateSettings(
                expectedRevision,
                settings -> settings.withPortal(portal)
        );
        return fromWrite(result, message("message.delvefold.admin.portal.saved"));
    }

    @Override
    public ServiceResult updateIdentity(ServerPlayer player, long expectedRevision,
            com.nightsta69.delvefold.config.model.WorldIdentitySettings identity) {
        requireConfigure(player);
        ConfigSnapshot before = DelvefoldConfigService.get().snapshot();
        if (before.settings().initialized()
                && before.settings().identity().terrainVariant() != identity.terrainVariant()) {
            return rejected(expectedRevision, message("message.delvefold.admin.identity.terrain_locked"));
        }
        if (before.settings().initialized()
                && before.settings().identity().geologyTheme() != identity.geologyTheme()) {
            return rejected(expectedRevision, message("message.delvefold.admin.identity.geology_locked"));
        }
        if (!before.settings().identity().renewal().equals(identity.renewal())
                && !AdminAccess.canManageWorld(player)) {
            return rejected(expectedRevision, message("message.delvefold.admin.identity.renewal_permission"));
        }
        ConfigWriteResult result = DelvefoldConfigService.get().updateSettings(expectedRevision,
                settings -> settings.withIdentity(identity));
        return fromWrite(result, message("message.delvefold.admin.identity.saved"));
    }

    @Override
    public ServiceResult performProfile(ServerPlayer player, long expectedOreRevision, ProfileOperation operation,
            String sourceId, String targetId, String json, boolean overwrite) {
        requireConfigure(player);
        try {
            return switch (operation) {
                case SELECT -> fromWrite(DelvefoldConfigService.get().activateProfile(expectedOreRevision, sourceId),
                        message("message.delvefold.admin.profile.activated", sourceId));
                case SAVE_CURRENT -> fromProfileWrite(
                        DelvefoldConfigService.get().saveCurrentProfileAs(targetId, overwrite));
                case DUPLICATE -> fromProfileWrite(
                        DelvefoldConfigService.get().duplicateProfile(sourceId, targetId, overwrite));
                case IMPORT_CLIPBOARD -> fromProfileWrite(
                        DelvefoldConfigService.get().importProfileJson(targetId, json, overwrite));
                case DELETE -> {
                    var result = DelvefoldConfigService.get().deleteProfile(sourceId);
                    yield new ServiceResult(result.deleted() ? ActionStatus.ACCEPTED : ActionStatus.REJECTED,
                            expectedOreRevision, result.message(), result.deleted());
                }
            };
        } catch (IOException | IllegalArgumentException exception) {
            return new ServiceResult(ActionStatus.ERROR, expectedOreRevision,
                    message("message.delvefold.admin.profile.failed", exception.getMessage()), false);
        }
    }

    private static ServiceResult fromProfileWrite(
            com.nightsta69.delvefold.config.OreProfileCatalog.ProfileWriteResult result) {
        String issues = result.issues().stream().map(ConfigIssueMessages::encode).findFirst().orElse("");
        String message = issues.isBlank()
                ? result.message()
                : message("message.delvefold.admin.profile.result_with_issue", result.message(), issues);
        return new ServiceResult(result.saved() ? ActionStatus.ACCEPTED : ActionStatus.REJECTED,
                DelvefoldConfigService.get().snapshot().ores().revision(), message, result.saved());
    }

    @Override
    public ServiceResult performBackup(ServerPlayer player, long expectedSettingsRevision,
            BackupOperation operation, String backupId) {
        requireWorldManagement(player);
        ConfigSnapshot snapshot = DelvefoldConfigService.get().snapshot();
        if (snapshot.settings().revision() != expectedSettingsRevision) {
            return stale(snapshot.settings().revision());
        }
        try {
            var server = player.getServer();
            var saveRoot = server.getWorldPath(LevelResource.ROOT);
            BackupCatalogCache backupCache = BackupCatalogCache.get();
            BackupCatalogCache.Snapshot cachedCatalog = backupCache.snapshot(saveRoot);
            if (operation == BackupOperation.RESTORE) {
                WorldBackupCatalog.BackupSummary summary = cachedCatalog.backups().stream()
                        .filter(candidate -> candidate.id().equals(backupId))
                        .findFirst()
                        .orElse(null);
                if (summary == null) {
                    return rejected(expectedSettingsRevision, cachedCatalog.refreshing()
                            ? message("message.delvefold.admin.backup.catalog_refreshing")
                            : message("message.delvefold.admin.backup.unknown", backupId));
                }
                WorldOperationPreview preview = WorldRestoreService.get().requestCached(
                        server, backupId, player.getGameProfile().getName(), summary);
                if (!preview.accepted()) {
                    return rejected(expectedSettingsRevision, preview.message());
                }
                WorldOperationResult confirmed = WorldRestoreService.get().confirm(
                        server, preview.confirmationToken());
                if (confirmed.success()) {
                    audit(player, AuditMutation.Operation.BACKUP_RESTORE_ACCEPTED,
                            AuditMutation.ObjectType.BACKUP, backupId, -1L, -1L);
                    backupCache.invalidateAndRefresh(saveRoot);
                }
                return new ServiceResult(confirmed.success() ? ActionStatus.ACCEPTED : ActionStatus.ERROR,
                        expectedSettingsRevision, confirmed.message(), true);
            }
            if (operation == BackupOperation.CANCEL_RESTORE) {
                String selectedBackupId = WorldRestoreService.get().selectedBackupId(server);
                WorldOperationResult cancelled = WorldRestoreService.get().cancel(server);
                if (cancelled.success()) {
                    backupCache.invalidateAndRefresh(saveRoot);
                    audit(player, AuditMutation.Operation.BACKUP_RESTORE_CANCELLED,
                            AuditMutation.ObjectType.BACKUP, selectedBackupId, -1L, -1L);
                }
                return new ServiceResult(cancelled.success() ? ActionStatus.ACCEPTED : ActionStatus.REJECTED,
                        expectedSettingsRevision, cancelled.message(), true);
            }
            if (operation == BackupOperation.VERIFY) {
                WorldBackupCatalog.BackupSummary summary = cachedCatalog.backups().stream()
                        .filter(candidate -> candidate.id().equals(backupId))
                        .findFirst()
                        .orElse(null);
                if (summary == null) {
                    return rejected(expectedSettingsRevision, cachedCatalog.refreshing()
                            ? message("message.delvefold.admin.backup.catalog_refreshing")
                            : message("message.delvefold.admin.backup.unknown", backupId));
                }
                var playerId = player.getUUID();
                var auditActor = player.getGameProfile().getName();
                BackupVerificationService verification = BackupVerificationService.forSave(
                        saveRoot);
                if (verification.isInFlight(backupId)) {
                    return accepted(expectedSettingsRevision,
                            message("message.delvefold.admin.backup.verification_running"), false);
                }
                var future = summary.legacy()
                        ? AsyncAuditMutationTracker.get().startTracked(saveRoot,
                                () -> verification.validateLegacyAndCreateManifestAsync(backupId),
                                (result, failure) -> {
                                    if (failure == null
                                            && result.status() == BackupVerificationResult.Status.LEGACY_UPGRADED) {
                                        audit(auditActor, AuditMutation.Operation.BACKUP_MANIFEST_CREATED,
                                                AuditMutation.ObjectType.BACKUP, backupId, -1L, -1L);
                                    }
                                })
                        : verification.verifyAsync(backupId);
                future.whenComplete((result, failure) -> server.execute(() -> {
                    backupCache.invalidateAndRefresh(saveRoot);
                    if (failure != null) {
                        LOGGER.log(System.Logger.Level.ERROR,
                                "Backup verification worker failed for " + backupId, failure);
                    }
                    ServerPlayer online = server.getPlayerList().getPlayer(playerId);
                    if (online == null) {
                        return;
                    }
                    long currentRevision;
                    try {
                        currentRevision = DelvefoldConfigService.get().snapshot().settings().revision();
                    } catch (IllegalStateException ignored) {
                        currentRevision = expectedSettingsRevision;
                    }
                    boolean successful = failure == null && result.successful();
                    String resultMessage = failure == null
                            ? result.message()
                            : message("message.delvefold.backup_verification.internal_error", backupId);
                    DelvefoldNetwork.sendAsyncResult(online, new ServiceResult(
                            successful ? ActionStatus.ACCEPTED : ActionStatus.ERROR,
                            currentRevision,
                            resultMessage,
                            true));
                }));
                return accepted(expectedSettingsRevision,
                        summary.legacy()
                                ? message("message.delvefold.admin.backup.legacy_validation_started")
                                : message("message.delvefold.admin.backup.verification_started"),
                        false);
            }
            if (operation == BackupOperation.DELETE) {
                var playerId = player.getUUID();
                var auditActor = player.getGameProfile().getName();
                var deletion = AsyncAuditMutationTracker.get().startTracked(saveRoot,
                        () -> backupCache.deleteAndRefreshAsync(server, backupId),
                        (deleted, failure) -> {
                            if (failure == null && Boolean.TRUE.equals(deleted)) {
                                audit(auditActor, AuditMutation.Operation.BACKUP_DELETED,
                                        AuditMutation.ObjectType.BACKUP, backupId, -1L, -1L);
                            }
                        });
                deletion.whenComplete((deleted, failure) ->
                        server.execute(() -> {
                            boolean success = failure == null && Boolean.TRUE.equals(deleted);
                            if (failure != null) {
                                LOGGER.log(System.Logger.Level.ERROR,
                                        "Could not delete Delvefold backup " + backupId, failure);
                            }
                            ServerPlayer online = server.getPlayerList().getPlayer(playerId);
                            if (online != null) {
                                String message = success
                                        ? message("message.delvefold.admin.backup.deleted", backupId)
                                        : backupDeleteFailure(backupId, failure);
                                DelvefoldNetwork.sendAsyncResult(online, new ServiceResult(
                                        success ? ActionStatus.ACCEPTED : ActionStatus.ERROR,
                                        expectedSettingsRevision, message, true));
                            }
                        }));
                return accepted(expectedSettingsRevision,
                        message("message.delvefold.admin.backup.delete_started"), false);
            }
            WorldBackupCatalog catalog = new WorldBackupCatalog(saveRoot);
            return switch (operation) {
                case PIN -> {
                    boolean changed = catalog.setPinned(backupId, true);
                    backupCache.invalidateAndRefresh(saveRoot);
                    if (changed) {
                        audit(player, AuditMutation.Operation.BACKUP_PINNED,
                                AuditMutation.ObjectType.BACKUP, backupId, -1L, -1L);
                    }
                    yield accepted(expectedSettingsRevision,
                            message("message.delvefold.admin.backup.pinned", backupId), true);
                }
                case UNPIN -> {
                    boolean changed = catalog.setPinned(backupId, false);
                    backupCache.invalidateAndRefresh(saveRoot);
                    if (changed) {
                        audit(player, AuditMutation.Operation.BACKUP_UNPINNED,
                                AuditMutation.ObjectType.BACKUP, backupId, -1L, -1L);
                    }
                    yield accepted(expectedSettingsRevision,
                            message("message.delvefold.admin.backup.unpinned", backupId), true);
                }
                case RESTORE, DELETE, VERIFY, CANCEL_RESTORE -> throw new IllegalStateException("Handled above");
            };
        } catch (IOException | IllegalArgumentException exception) {
            return new ServiceResult(ActionStatus.ERROR, expectedSettingsRevision,
                    message("message.delvefold.admin.backup.failed", exception.getMessage()), false);
        }
    }

    @Override
    public ServiceResult perform(
            ServerPlayer player,
            long expectedRevision,
            AdminOperation operation,
            String confirmation
    ) {
        if (operation.permissionLevel() >= AdminAccess.WORLD_MANAGEMENT_PERMISSION) {
            requireWorldManagement(player);
        } else {
            requireConfigure(player);
        }
        ConfigSnapshot snapshot = DelvefoldConfigService.get().snapshot();
        if (operation != AdminOperation.REFRESH && operation != AdminOperation.VALIDATE_CONFIG
                && snapshot.settings().revision() != expectedRevision) {
            return stale(snapshot.settings().revision());
        }
        return switch (operation) {
            case REFRESH -> accepted(snapshot.settings().revision(),
                    message("message.delvefold.admin.refreshed"), true);
            case VALIDATE_CONFIG -> validate(snapshot);
            case RELOAD_CONFIG -> reload(snapshot);
            case DELETE_WORLD -> scheduleAndConfirm(player, WorldOperationRequest.delete());
            case RECREATE_WORLD -> scheduleAndConfirm(player,
                    WorldOperationRequest.recreate(
                            recreateTerrain(confirmation, snapshot.settings().terrainMode()),
                            recreateVariant(confirmation, snapshot.settings().identity().terrainVariant()),
                            recreateGeologyTheme(confirmation, snapshot.settings().identity().geologyTheme())));
            case CANCEL_PENDING_RESET -> cancelPendingWorldOperation(player, snapshot.settings().revision());
        };
    }

    private static ServiceResult scheduleAndConfirm(ServerPlayer player, WorldOperationRequest request) {
        long beforeRevision = DelvefoldConfigService.get().snapshot().settings().revision();
        WorldOperationPreview preview = WorldOperationService.get().request(
                player.getServer(),
                new WorldOperationRequest(
                        request.type(), request.targetTerrain(), request.targetVariant(), request.targetGeologyTheme(),
                        request.targetOrePreset(), request.targetGameplayPreset(),
                        BackupMode.KEEP_BACKUP, request.resetOreConfiguration()),
                player.getGameProfile().getName()
        );
        if (!preview.accepted()) {
            return rejected(DelvefoldConfigService.get().snapshot().settings().revision(), preview.message());
        }
        WorldOperationResult result = WorldOperationService.get().confirm(player.getServer(), preview.confirmationToken());
        if (result.success()) {
            audit(player, AuditMutation.Operation.WORLD_OPERATION_ACCEPTED,
                    AuditMutation.ObjectType.WORLD, "mining_world", beforeRevision, beforeRevision);
        }
        String message = message("message.delvefold.admin.world_operation.scheduled",
                result.message(), preview.estimatedBytes());
        return new ServiceResult(
                result.success() ? ActionStatus.ACCEPTED : ActionStatus.ERROR,
                DelvefoldConfigService.get().snapshot().settings().revision(),
                message,
                true
        );
    }

    private static ServiceResult cancelPendingWorldOperation(ServerPlayer player, long revision) {
        WorldOperationResult result = WorldOperationService.get().cancelConfirmed(player.getServer());
        if (result.success()) {
            audit(player, AuditMutation.Operation.WORLD_OPERATION_CANCELLED,
                    AuditMutation.ObjectType.WORLD, "mining_world", revision, revision);
        }
        return fromOperation(result, revision);
    }

    private static void audit(ServerPlayer player, AuditMutation.Operation operation,
            AuditMutation.ObjectType objectType, String objectId, long oldRevision, long newRevision) {
        audit(player.getGameProfile().getName(), operation, objectType, objectId, oldRevision, newRevision);
    }

    private static void audit(String actor, AuditMutation.Operation operation,
            AuditMutation.ObjectType objectType, String objectId, long oldRevision, long newRevision) {
        DelvefoldAuditService.get().record(new AuditMutation(
                actor, operation, objectType, objectId, oldRevision, newRevision));
    }

    private static String backupDeleteFailure(String backupId, Throwable failure) {
        BackupDeletionGuard.DeletionRejectedException rejection = deletionRejection(failure);
        if (rejection == null) {
            return message("message.delvefold.admin.backup.delete_failed", backupId);
        }
        String key = switch (rejection.reason()) {
            case REFERENCED -> "message.delvefold.backup_delete.referenced";
            case IN_PROGRESS -> "message.delvefold.backup_delete.in_progress";
            case SESSION_CLOSED -> "message.delvefold.backup_delete.session_closed";
        };
        return message(key, backupId);
    }

    private static BackupDeletionGuard.DeletionRejectedException deletionRejection(Throwable failure) {
        Throwable current = failure;
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

    private static TerrainMode recreateTerrain(String confirmation, TerrainMode fallback) {
        if (confirmation == null) {
            return fallback;
        }
        int separator = confirmation.indexOf(':');
        if (separator < 0 || separator + 1 >= confirmation.length()) {
            return fallback;
        }
        try {
            String value = confirmation.substring(separator + 1).split(":", 2)[0];
            return TerrainMode.parse(value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static com.nightsta69.delvefold.config.model.TerrainVariant recreateVariant(
            String confirmation, com.nightsta69.delvefold.config.model.TerrainVariant fallback) {
        if (confirmation == null) {
            return fallback;
        }
        String[] parts = confirmation.split(":");
        if (parts.length < 3) {
            return fallback;
        }
        try {
            return com.nightsta69.delvefold.config.model.TerrainVariant.valueOf(
                    parts[2].toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static GeologyTheme recreateGeologyTheme(String confirmation, GeologyTheme fallback) {
        if (confirmation == null) {
            return fallback;
        }
        String[] parts = confirmation.split(":");
        if (parts.length < 4) {
            return fallback;
        }
        try {
            return GeologyTheme.parse(parts[3]);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static ServiceResult validate(ConfigSnapshot snapshot) {
        try {
            ConfigLoadResult result = DelvefoldConfigService.get().validateDisk();
            ValidationReport report = new ValidationReport(result.issues());
            boolean valid = report.valid() && !result.usedFallback();
            String message = message("message.delvefold.admin.validation.result",
                    report.errorCount(), report.warningCount());
            return new ServiceResult(valid ? ActionStatus.ACCEPTED : ActionStatus.REJECTED,
                    snapshot.settings().revision(), message, true);
        } catch (IOException exception) {
            return new ServiceResult(ActionStatus.ERROR, snapshot.settings().revision(),
                    message("message.delvefold.admin.validation.failed", exception.getMessage()), false);
        }
    }

    private static ServiceResult reload(ConfigSnapshot snapshot) {
        try {
            ConfigLoadResult result = DelvefoldConfigService.get().reload();
            if (result.usedFallback()) {
                return rejected(snapshot.settings().revision(),
                        message("message.delvefold.admin.reload.rejected"));
            }
            return accepted(result.snapshot().settings().revision(),
                    message("message.delvefold.admin.reload.accepted"), true);
        } catch (IOException exception) {
            return new ServiceResult(ActionStatus.ERROR, snapshot.settings().revision(),
                    message("message.delvefold.admin.reload.failed", exception.getMessage()), false);
        }
    }

    static OreRule fromDraft(AdminSnapshot.OreRuleDraft draft) {
        List<OreTarget> targets = draft.variants().stream().map(variant -> {
            String tag = stripHash(variant.replaceTag());
            return new OreTarget(variant.blockId(), variant.blockTag(), variant.state(), tag, variant.weight());
        }).toList();
        List<SpawnBand> bands = draft.bands().stream().map(DefaultDelvefoldAdminService::fromDraft).toList();
        Set<TerrainMode> terrainModes = Set.copyOf(draft.terrainModes());
        return new OreRule(
                draft.id(),
                draft.enabled(),
                draft.required(),
                terrainModes,
                targets,
                new BiomeFilter(draft.biomeIncludes(), draft.biomeExcludes()),
                bands
        );
    }

    private static SpawnBand fromDraft(AdminSnapshot.OreBandDraft draft) {
        Integer peak = draft.distribution() == HeightDistribution.TRIANGLE ? draft.peakY() : null;
        Integer plateauMin = draft.distribution() == HeightDistribution.TRAPEZOID ? draft.plateauMinY() : null;
        Integer plateauMax = draft.distribution() == HeightDistribution.TRAPEZOID ? draft.plateauMaxY() : null;
        return new SpawnBand(
                draft.id(), draft.veinSize(), draft.attemptsPerChunk(), draft.distribution(),
                draft.minY(), draft.maxY(), peak, plateauMin, plateauMax, draft.discardOnAirExposure(),
                draft.placement(), draft.province()
        );
    }

    static AdminSnapshot.OreRuleDraft toDraft(OreRule rule) {
        String primary = rule.targets().stream().map(OreTarget::block).filter(block -> !block.isBlank())
                .findFirst().orElse("minecraft:air");
        return new AdminSnapshot.OreRuleDraft(
                rule.id(),
                rule.enabled(),
                rule.required(),
                primary,
                rule.targets().stream()
                        .map(target -> new AdminSnapshot.OreVariantDraft(
                                target.block(), target.blockTag(), target.replaceTag(), target.state(), target.weight()))
                        .toList(),
                List.copyOf(rule.terrainModes()),
                rule.biomes().include(),
                rule.biomes().exclude(),
                rule.bands().stream().map(DefaultDelvefoldAdminService::toDraft).toList()
        );
    }

    private static AdminSnapshot.OreBandDraft toDraft(SpawnBand band) {
        return new AdminSnapshot.OreBandDraft(
                band.id(),
                band.veinSize(),
                band.attemptsPerChunk(),
                band.distribution(),
                band.minY(),
                band.maxY(),
                band.peakY() == null ? 0 : band.peakY(),
                band.plateauMinY() == null ? band.minY() : band.plateauMinY(),
                band.plateauMaxY() == null ? band.maxY() : band.plateauMaxY(),
                band.discardOnAirExposure(),
                band.placement(),
                band.province()
        );
    }

    private static ServiceResult fromWrite(ConfigWriteResult result, String successMessage) {
        if (result.saved()) {
            return accepted(Math.max(
                    result.snapshot().ores().revision(),
                    result.snapshot().settings().revision()), successMessage, true);
        }
        boolean stale = result.issues().stream().anyMatch(issue -> "revision.stale".equals(issue.code()));
        String message = result.issues().isEmpty()
                ? message("message.delvefold.admin.change_rejected")
                : ConfigIssueMessages.encode(result.issues().getFirst());
        long revision = result.snapshot() == null ? 0L : result.snapshot().settings().revision();
        return new ServiceResult(stale ? ActionStatus.STALE : ActionStatus.REJECTED, revision, message, stale);
    }

    private static ServiceResult fromOperation(WorldOperationResult result, long revision) {
        return new ServiceResult(result.success() ? ActionStatus.ACCEPTED : ActionStatus.REJECTED,
                revision, result.message(), true);
    }

    private static ServiceResult accepted(long revision, String message, boolean refresh) {
        return new ServiceResult(ActionStatus.ACCEPTED, revision, message, refresh);
    }

    private static ServiceResult rejected(long revision, String message) {
        return new ServiceResult(ActionStatus.REJECTED, revision, message, false);
    }

    private static ServiceResult stale(long currentRevision) {
        return new ServiceResult(ActionStatus.STALE, currentRevision,
                message("message.delvefold.admin.stale"), true);
    }

    private static String message(String translationKey, Object... arguments) {
        return AdminLocalizedMessage.encode(translationKey, arguments);
    }

    private static String stripHash(String value) {
        return value != null && value.startsWith("#") ? value.substring(1) : value;
    }

    private static void requireConfigure(ServerPlayer player) {
        if (!AdminAccess.canConfigure(player)) {
            throw new SecurityException("Player is not allowed to configure Delvefold");
        }
    }

    private static void requireWorldManagement(ServerPlayer player) {
        if (!AdminAccess.canManageWorld(player)) {
            throw new SecurityException("Player is not allowed to manage the Delvefold world");
        }
    }
}
