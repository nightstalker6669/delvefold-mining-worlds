package com.nightsta69.delvefold.admin;

import com.nightsta69.delvefold.config.AdminAccess;
import com.nightsta69.delvefold.config.ConfigLoadResult;
import com.nightsta69.delvefold.config.ConfigSnapshot;
import com.nightsta69.delvefold.config.ConfigWriteResult;
import com.nightsta69.delvefold.config.DelvefoldConfigService;
import com.nightsta69.delvefold.config.model.BiomeFilter;
import com.nightsta69.delvefold.config.model.GameplaySettings;
import com.nightsta69.delvefold.config.model.HeightDistribution;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.OreRule;
import com.nightsta69.delvefold.config.model.OreTarget;
import com.nightsta69.delvefold.config.model.SpawnBand;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.validation.ConfigIssue;
import com.nightsta69.delvefold.config.validation.ValidationReport;
import com.nightsta69.delvefold.network.model.ActionStatus;
import com.nightsta69.delvefold.network.model.AdminOperation;
import com.nightsta69.delvefold.network.model.AdminSnapshot;
import com.nightsta69.delvefold.network.ProtocolLimits;
import com.nightsta69.delvefold.network.service.DelvefoldAdminService;
import com.nightsta69.delvefold.reset.BackupMode;
import com.nightsta69.delvefold.reset.WorldOperationPreview;
import com.nightsta69.delvefold.reset.WorldOperationRequest;
import com.nightsta69.delvefold.reset.WorldOperationResult;
import com.nightsta69.delvefold.reset.WorldOperationService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;

/** Connects the bounded GUI protocol to the same config/reset services used by commands. */
public final class DefaultDelvefoldAdminService implements DelvefoldAdminService {
    @Override
    public AdminSnapshot snapshot(ServerPlayer player, int requestedOrePage, int requestedOrePageSize) {
        requireConfigure(player);
        ConfigSnapshot snapshot = DelvefoldConfigService.get().snapshot();
        var settings = snapshot.settings();
        List<String> diagnostics = new ArrayList<>();
        diagnostics.add("Config hash: " + snapshot.diskHash());
        diagnostics.add("Loaded: " + snapshot.loadedAt());
        diagnostics.add("Generation epoch: " + settings.generationEpoch());
        for (ConfigIssue issue : snapshot.validation().issues()) {
            diagnostics.add(issue.severity() + " " + issue.path() + ": " + issue.message());
        }
        String portalStatus;
        if (!settings.initialized()) {
            portalStatus = "Inactive. Initialize Delvefold through this GUI or /miningworlds initialize; portal use never initializes a world.";
        } else if (WorldOperationService.get().isEntryBlocked()) {
            portalStatus = "Temporarily blocked because a world operation is pending.";
        } else if (!settings.portal().enabled()) {
            portalStatus = "Disabled in settings.";
        } else {
            portalStatus = "Ready for " + settings.terrainMode().serializedName() + " terrain; player-only, "
                    + settings.portal().cooldownSeconds() + " second cooldown.";
        }
        String worldStatus = settings.initialized()
                ? "Terrain " + settings.terrainMode().serializedName() + ", generation epoch " + settings.generationEpoch()
                : "No mining world is initialized. Existing portals are inactive.";
        int pageSize = Math.max(1, Math.min(requestedOrePageSize, ProtocolLimits.MAX_ORE_RULES_PER_PAGE));
        int totalRules = snapshot.ores().rules().size();
        int maximumPage = Math.max(0, (totalRules - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedOrePage, maximumPage));
        int start = Math.min(totalRules, page * pageSize);
        int end = Math.min(totalRules, start + pageSize);
        List<AdminSnapshot.OreRuleDraft> pageRules = snapshot.ores().rules().subList(start, end).stream()
                .map(DefaultDelvefoldAdminService::toDraft)
                .toList();
        return new AdminSnapshot(
                snapshot.ores().revision(),
                settings.revision(),
                true,
                settings.initialized(),
                settings.terrainMode(),
                settings.orePreset(),
                settings.gameplay(),
                portalStatus,
                worldStatus,
                WorldOperationService.get().isEntryBlocked(),
                diagnostics,
                totalRules,
                page,
                pageRules
        );
    }

    @Override
    public ServiceResult initialize(
            ServerPlayer player,
            long expectedOreRevision,
            long expectedSettingsRevision,
            TerrainMode terrainMode,
            OrePreset orePreset,
            GameplaySettings gameplay
    ) {
        requireConfigure(player);
        ConfigWriteResult initialized = DelvefoldConfigService.get().initialize(
                expectedOreRevision,
                expectedSettingsRevision,
                terrainMode,
                orePreset,
                gameplay.preset()
        );
        if (!initialized.saved()) {
            return fromWrite(initialized, "Initialization was rejected");
        }
        ConfigSnapshot after = DelvefoldConfigService.get().snapshot();
        if (!after.settings().gameplay().equals(gameplay)) {
            ConfigWriteResult customized = DelvefoldConfigService.get().updateSettings(
                    after.settings().revision(),
                    settings -> settings.withGameplay(gameplay)
            );
            if (!customized.saved()) {
                return fromWrite(customized, "Initialized, but custom gameplay toggles were rejected");
            }
        }
        return accepted(DelvefoldConfigService.get().snapshot().settings().revision(),
                "Delvefold initialized. Portal activation is now enabled.", true);
    }

    @Override
    public ServiceResult saveOreRule(
            ServerPlayer player,
            long expectedRevision,
            AdminSnapshot.OreRuleDraft draft,
            boolean createOnly) {
        requireConfigure(player);
        ConfigSnapshot before = DelvefoldConfigService.get().snapshot();
        OreRule existing = before.ores().rules().stream()
                .filter(rule -> rule.id().equals(draft.id()))
                .findFirst()
                .orElse(null);
        OreRule replacement = fromDraft(draft, existing);
        ConfigWriteResult result = DelvefoldConfigService.get().saveOreRule(
                expectedRevision, replacement, createOnly);
        return fromWrite(result, "Saved ore rule " + replacement.id());
    }

    @Override
    public ServiceResult deleteOreRule(ServerPlayer player, long expectedRevision, String ruleId) {
        requireConfigure(player);
        ConfigSnapshot before = DelvefoldConfigService.get().snapshot();
        if (before.ores().rules().stream().noneMatch(rule -> rule.id().equals(ruleId))) {
            return rejected(expectedRevision, "Unknown ore rule: " + ruleId);
        }
        ConfigWriteResult result = DelvefoldConfigService.get().updateOres(expectedRevision, document ->
                document.nextRevision(document.rules().stream().filter(rule -> !rule.id().equals(ruleId)).toList(), "custom"));
        return fromWrite(result, "Deleted ore rule " + ruleId);
    }

    @Override
    public ServiceResult updateGameplay(ServerPlayer player, long expectedRevision, GameplaySettings gameplay) {
        requireConfigure(player);
        ConfigWriteResult result = DelvefoldConfigService.get().updateSettings(
                expectedRevision,
                settings -> settings.withGameplay(gameplay)
        );
        return fromWrite(result, "Gameplay settings saved");
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
            case REFRESH -> accepted(snapshot.settings().revision(), "Refreshed", true);
            case VALIDATE_CONFIG -> validate(snapshot);
            case RELOAD_CONFIG -> reload(snapshot);
            case DELETE_WORLD -> scheduleAndConfirm(player, WorldOperationRequest.delete());
            case RECREATE_WORLD -> scheduleAndConfirm(player,
                    WorldOperationRequest.recreate(recreateTerrain(confirmation, snapshot.settings().terrainMode())));
            case CANCEL_PENDING_RESET -> fromOperation(
                    WorldOperationService.get().cancelConfirmed(player.getServer()), snapshot.settings().revision());
        };
    }

    private static ServiceResult scheduleAndConfirm(ServerPlayer player, WorldOperationRequest request) {
        WorldOperationPreview preview = WorldOperationService.get().request(
                player.getServer(),
                new WorldOperationRequest(
                        request.type(), request.targetTerrain(), request.targetOrePreset(), request.targetGameplayPreset(),
                        BackupMode.KEEP_BACKUP, request.resetOreConfiguration()),
                player.getGameProfile().getName()
        );
        if (!preview.accepted()) {
            return rejected(DelvefoldConfigService.get().snapshot().settings().revision(), preview.message());
        }
        WorldOperationResult result = WorldOperationService.get().confirm(player.getServer(), preview.confirmationToken());
        String message = result.message() + " A recoverable timestamped backup is enabled (estimated old data: "
                + preview.estimatedBytes() + " bytes).";
        return new ServiceResult(
                result.success() ? ActionStatus.ACCEPTED : ActionStatus.ERROR,
                DelvefoldConfigService.get().snapshot().settings().revision(),
                message,
                true
        );
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
            return TerrainMode.parse(confirmation.substring(separator + 1));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static ServiceResult validate(ConfigSnapshot snapshot) {
        try {
            ConfigLoadResult result = DelvefoldConfigService.get().validateDisk();
            ValidationReport report = new ValidationReport(result.issues());
            boolean valid = report.valid() && !result.usedFallback();
            String message = "Disk validation found " + report.errorCount() + " error(s) and "
                    + report.warningCount() + " warning(s).";
            return new ServiceResult(valid ? ActionStatus.ACCEPTED : ActionStatus.REJECTED,
                    snapshot.settings().revision(), message, true);
        } catch (IOException exception) {
            return new ServiceResult(ActionStatus.ERROR, snapshot.settings().revision(),
                    "Disk validation failed: " + exception.getMessage(), false);
        }
    }

    private static ServiceResult reload(ConfigSnapshot snapshot) {
        try {
            ConfigLoadResult result = DelvefoldConfigService.get().reload();
            if (result.usedFallback()) {
                return rejected(snapshot.settings().revision(), "Rejected disk changes; the last known-good snapshot remains active.");
            }
            return accepted(result.snapshot().settings().revision(), "JSON configuration reloaded.", true);
        } catch (IOException exception) {
            return new ServiceResult(ActionStatus.ERROR, snapshot.settings().revision(),
                    "Reload failed: " + exception.getMessage(), false);
        }
    }

    private static OreRule fromDraft(AdminSnapshot.OreRuleDraft draft, OreRule existing) {
        Map<String, OreTarget> oldTargets = new LinkedHashMap<>();
        if (existing != null) {
            for (OreTarget target : existing.targets()) {
                oldTargets.put(target.block(), target);
            }
        }
        List<OreTarget> targets = draft.variants().stream().map(variant -> {
            OreTarget old = oldTargets.get(variant.blockId());
            String tag = stripHash(variant.replaceTag());
            return new OreTarget(variant.blockId(), old == null ? Map.of() : old.state(), tag);
        }).toList();
        List<SpawnBand> bands = draft.bands().stream().map(DefaultDelvefoldAdminService::fromDraft).toList();
        Set<TerrainMode> terrainModes = Set.copyOf(draft.terrainModes());
        return new OreRule(
                draft.id(),
                draft.enabled(),
                draft.required(),
                terrainModes,
                targets,
                existing == null ? BiomeFilter.ALL_MINING_BIOMES : existing.biomes(),
                bands
        );
    }

    private static SpawnBand fromDraft(AdminSnapshot.OreBandDraft draft) {
        Integer peak = draft.distribution() == HeightDistribution.TRIANGLE ? draft.peakY() : null;
        Integer plateauMin = draft.distribution() == HeightDistribution.TRAPEZOID ? draft.plateauMinY() : null;
        Integer plateauMax = draft.distribution() == HeightDistribution.TRAPEZOID ? draft.plateauMaxY() : null;
        return new SpawnBand(
                draft.id(), draft.veinSize(), draft.attemptsPerChunk(), draft.distribution(),
                draft.minY(), draft.maxY(), peak, plateauMin, plateauMax, draft.discardOnAirExposure()
        );
    }

    private static AdminSnapshot.OreRuleDraft toDraft(OreRule rule) {
        String primary = rule.targets().isEmpty() ? "minecraft:air" : rule.targets().getFirst().block();
        return new AdminSnapshot.OreRuleDraft(
                rule.id(),
                rule.enabled(),
                rule.required(),
                primary,
                rule.targets().stream()
                        .map(target -> new AdminSnapshot.OreVariantDraft(target.block(), target.replaceTag()))
                        .toList(),
                List.copyOf(rule.terrainModes()),
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
                band.discardOnAirExposure()
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
                ? successMessage + " was rejected"
                : result.issues().getFirst().message();
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
                "The configuration changed while this screen was open. It has been refreshed.", true);
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
