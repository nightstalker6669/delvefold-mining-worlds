package com.nightsta69.delvefold.admin;

import com.nightsta69.delvefold.config.AdminAccess;
import com.nightsta69.delvefold.config.ConfigSnapshot;
import com.nightsta69.delvefold.config.DelvefoldConfigService;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.validation.ConfigIssue;
import com.nightsta69.delvefold.config.validation.ConfigIssueMessages;
import com.nightsta69.delvefold.diagnostics.DelvefoldDoctorService;
import com.nightsta69.delvefold.network.ProtocolLimits;
import com.nightsta69.delvefold.network.model.AdminSnapshot;
import com.nightsta69.delvefold.reset.BackupCatalogCache;
import com.nightsta69.delvefold.reset.WorldOperationService;
import com.nightsta69.delvefold.reset.WorldRestoreService;
import com.nightsta69.delvefold.world.landmark.catalog.LandmarkCatalogService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/** Assembles the bounded, server-authoritative administration snapshot consumed by the dashboard. */
final class AdminSnapshotAssembler {
    private AdminSnapshotAssembler() {}

    static AdminSnapshot assemble(ServerPlayer player, int requestedOrePage, int requestedOrePageSize) {
        ConfigSnapshot snapshot = DelvefoldConfigService.get().snapshot();
        boolean compatible = !DelvefoldConfigService.get().isReadOnlyIncompatible();
        var settings = snapshot.settings();
        var saveRoot = player.getServer().getWorldPath(LevelResource.ROOT);
        BackupCatalogCache.Snapshot backupCatalog = BackupCatalogCache.get().snapshot(saveRoot);
        List<String> diagnostics = new ArrayList<>();
        diagnostics.add(message("message.delvefold.admin.diagnostic.config_hash", snapshot.diskHash()));
        diagnostics.add(message("message.delvefold.admin.diagnostic.loaded", snapshot.loadedAt()));
        diagnostics.add(message("message.delvefold.admin.diagnostic.generation_epoch", settings.generationEpoch()));
        var landmarkCatalog = LandmarkCatalogService.get();
        var landmarkDiagnostics = landmarkCatalog.diagnostics();
        diagnostics.add(message(
                landmarkDiagnostics.lastReloadAccepted()
                        ? "message.delvefold.admin.diagnostic.landmarks.applied"
                        : "message.delvefold.admin.diagnostic.landmarks.rejected",
                landmarkCatalog.snapshot().revision(),
                landmarkCatalog.snapshot().definitions().size()));
        landmarkDiagnostics.errors().stream()
                .limit(8)
                .forEach(
                        error -> diagnostics.add(message("message.delvefold.admin.diagnostic.landmarks.error", error)));
        if (!compatible) {
            diagnostics.add(DelvefoldConfigService.get().compatibilityMessage());
        }
        if (backupCatalog.refreshing()) {
            diagnostics.add(message("message.delvefold.admin.diagnostic.backups.refreshing"));
        }
        if (!backupCatalog.lastError().isBlank()) {
            diagnostics.add(message("message.delvefold.admin.diagnostic.backups.error", backupCatalog.lastError()));
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
            diagnostics.add(message("message.delvefold.admin.diagnostic.doctor_unavailable", exception.getMessage()));
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
                    ? message(
                            "message.delvefold.admin.portal.ready.central_hub",
                            java.util.Objects.requireNonNull(settings.terrainMode(), "initialized terrain")
                                    .serializedName(),
                            settings.portal().hub().x(),
                            settings.portal().hub().z(),
                            settings.portal().hub().protectionRadius(),
                            settings.portal().cooldownSeconds())
                    : message(
                            "message.delvefold.admin.portal.ready.coordinate_linked",
                            java.util.Objects.requireNonNull(settings.terrainMode(), "initialized terrain")
                                    .serializedName(),
                            settings.portal().cooldownSeconds());
        }
        String worldStatus = settings.initialized()
                ? message(
                        "message.delvefold.admin.world.ready",
                        java.util.Objects.requireNonNull(settings.terrainMode(), "initialized terrain")
                                .serializedName(),
                        settings.generationEpoch())
                : message("message.delvefold.admin.world.uninitialized");
        int totalRules = snapshot.ores().rules().size();
        PageBounds pageBounds = pageBounds(totalRules, requestedOrePage, requestedOrePageSize);
        List<AdminSnapshot.OreRuleDraft> pageRules =
                snapshot.ores().rules().subList(pageBounds.start(), pageBounds.end()).stream()
                        .map(AdminOreDraftMapper::toDraft)
                        .toList();
        List<AdminSnapshot.ProfileDraft> profiles;
        try {
            profiles = DelvefoldConfigService.get().listProfiles().stream()
                    .map(profile -> new AdminSnapshot.ProfileDraft(
                            profile.id(),
                            profile.builtIn(),
                            profile.localOverride(),
                            profile.ruleCount(),
                            profile.revision(),
                            profile.issues().stream()
                                    .noneMatch(issue -> issue.severity()
                                            == com.nightsta69.delvefold.config.validation.IssueSeverity.ERROR)))
                    .toList();
        } catch (IOException exception) {
            diagnostics.add(message("message.delvefold.admin.diagnostic.profiles.error", exception.getMessage()));
            profiles = List.of();
        }
        List<AdminSnapshot.BackupDraft> backups = backupCatalog.backups().stream()
                .limit(ProtocolLimits.MAX_BACKUPS)
                .map(backup -> new AdminSnapshot.BackupDraft(
                        backup.id(),
                        backup.createdAtEpochMillis(),
                        backup.operation(),
                        backup.terrain(),
                        backup.sizeBytes(),
                        backup.pinned(),
                        backup.restorable(),
                        backup.valid(),
                        backup.manifestPresent(),
                        backup.verified(),
                        backup.legacy()))
                .toList();
        return new AdminSnapshot(
                snapshot.ores().revision(),
                settings.revision(),
                compatible,
                settings.initialized(),
                java.util.Objects.requireNonNullElse(settings.terrainMode(), TerrainMode.FLAT),
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
                pageBounds.page(),
                pageRules);
    }

    static PageBounds pageBounds(int totalRules, int requestedPage, int requestedPageSize) {
        int pageSize = Math.max(1, Math.min(requestedPageSize, ProtocolLimits.MAX_ORE_RULES_PER_PAGE));
        int maximumPage = Math.max(0, (totalRules - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, maximumPage));
        int start = Math.min(totalRules, page * pageSize);
        int end = Math.min(totalRules, start + pageSize);
        return new PageBounds(page, start, end);
    }

    private static String message(String translationKey, @Nullable Object... arguments) {
        return AdminLocalizedMessage.encode(translationKey, arguments);
    }

    record PageBounds(int page, int start, int end) {}
}
