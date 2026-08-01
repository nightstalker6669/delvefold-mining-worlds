package com.nightsta69.delvefold.admin;

import com.mojang.logging.LogUtils;
import com.nightsta69.delvefold.config.AdminAccess;
import com.nightsta69.delvefold.config.ConfigSnapshot;
import com.nightsta69.delvefold.config.DelvefoldConfigService;
import com.nightsta69.delvefold.config.importer.MinecraftOreImportRegistry;
import com.nightsta69.delvefold.config.importer.OreImportDiscovery;
import com.nightsta69.delvefold.config.importer.OreImportFingerprints;
import com.nightsta69.delvefold.config.importer.OreImportModels;
import com.nightsta69.delvefold.config.importer.OreImportPlanner;
import com.nightsta69.delvefold.config.importer.OreImportSessionService;
import com.nightsta69.delvefold.config.importer.OreImportSessionService.PreviewRequest;
import com.nightsta69.delvefold.config.importer.OreImportSessionService.SnapshotBinding;
import com.nightsta69.delvefold.network.OreImportNetworkViews;
import com.nightsta69.delvefold.network.model.ActionStatus;
import com.nightsta69.delvefold.network.model.OreImportViews.PreviewView;
import com.nightsta69.delvefold.network.model.OreImportViews.ScanView;
import com.nightsta69.delvefold.network.service.DelvefoldAdminService.ServiceResult;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

/** Permission-checked server boundary for scan, preview, paging, and strict profile creation. */
public final class OreImportAdminService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final OreImportAdminService INSTANCE = new OreImportAdminService(
            OreImportSessionService.get());

    private final OreImportSessionService sessions;

    OreImportAdminService(OreImportSessionService sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    public static OreImportAdminService get() {
        return INSTANCE;
    }

    public ViewResult<ScanView> scan(
            ServerPlayer player, long expectedOreRevision, boolean includeVanilla) {
        requireConfigure(player);
        ConfigSnapshot snapshot = DelvefoldConfigService.get().snapshot();
        if (snapshot.ores().revision() != expectedOreRevision) {
            return ViewResult.rejected(ActionStatus.STALE,
                    "The active ore profile changed; refresh before scanning again.");
        }
        var admission = sessions.mayIssueScan(player.getUUID());
        if (!admission.accepted()) {
            return ViewResult.rejected(ActionStatus.REJECTED,
                    "Ore discovery is rate limited; wait a moment and try again.");
        }
        try {
            ImportContext context = context();
            OreImportModels.DiscoveryResult discovery = OreImportDiscovery.discover(
                    context.registry(), new OreImportModels.DiscoveryOptions(includeVanilla));
            var issued = sessions.issueScan(player.getUUID(), context.binding(), discovery);
            return ViewResult.accepted(OreImportNetworkViews.scan(
                    issued, snapshot.ores().revision(), snapshot.ores().profile(), 0));
        } catch (RuntimeException exception) {
            LOGGER.warn("Ore discovery failed for {}", player.getGameProfile().getName(), exception);
            return ViewResult.rejected(ActionStatus.ERROR,
                    "Ore discovery failed; see the server log for details.");
        }
    }

    public ViewResult<ScanView> scanPage(ServerPlayer player, String scanToken, int page) {
        requireConfigure(player);
        try {
            ImportContext context = context();
            var access = sessions.accessScan(player.getUUID(), scanToken, context.binding());
            if (!access.accepted()) {
                return sessionRejected(access.status());
            }
            ConfigSnapshot snapshot = DelvefoldConfigService.get().snapshot();
            return ViewResult.accepted(OreImportNetworkViews.scan(
                    access.scan(), snapshot.ores().revision(), snapshot.ores().profile(), page));
        } catch (RuntimeException exception) {
            return ViewResult.rejected(ActionStatus.ERROR, "Could not read the ore-discovery page.");
        }
    }

    public ViewResult<PreviewView> preview(
            ServerPlayer player, String scanToken, List<String> selectedGroupIds) {
        requireConfigure(player);
        try {
            ImportContext context = context();
            var access = sessions.accessScan(player.getUUID(), scanToken, context.binding());
            if (!access.accepted()) {
                return sessionRejected(access.status());
            }
            Map<String, OreImportModels.Group> available = new LinkedHashMap<>();
            access.scan().discovery().groups().forEach(group -> available.put(group.id(), group));
            List<OreImportModels.Group> selected = selectedGroupIds.stream()
                    .map(available::get)
                    .toList();
            if (selected.stream().anyMatch(Objects::isNull)) {
                return ViewResult.rejected(ActionStatus.REJECTED,
                        "The selection contains an ore group that is not part of this scan.");
            }
            ConfigSnapshot snapshot = DelvefoldConfigService.get().snapshot();
            OreImportModels.Plan plan = OreImportPlanner.plan(
                    snapshot.ores(), selected, context.registry(), context.registry());
            PreviewRequest request = new PreviewRequest(snapshot.ores().profile(), selectedGroupIds);
            var issue = sessions.issuePreview(
                    player.getUUID(), scanToken, context.binding(), request, plan);
            if (!issue.accepted()) {
                return sessionRejected(issue.status());
            }
            return ViewResult.accepted(OreImportNetworkViews.preview(
                    issue.preview(), snapshot.ores().revision(), 0));
        } catch (IllegalArgumentException exception) {
            return ViewResult.rejected(ActionStatus.REJECTED, exception.getMessage());
        } catch (RuntimeException exception) {
            LOGGER.warn("Ore import preview failed for {}", player.getGameProfile().getName(), exception);
            return ViewResult.rejected(ActionStatus.ERROR,
                    "Ore import preview failed; see the server log for details.");
        }
    }

    public ViewResult<PreviewView> previewPage(ServerPlayer player, String commitToken, int page) {
        requireConfigure(player);
        try {
            ImportContext context = context();
            var access = sessions.accessPreview(player.getUUID(), commitToken, context.binding());
            if (!access.accepted()) {
                return sessionRejected(access.status());
            }
            return ViewResult.accepted(OreImportNetworkViews.preview(
                    access.preview(), DelvefoldConfigService.get().snapshot().ores().revision(), page));
        } catch (RuntimeException exception) {
            return ViewResult.rejected(ActionStatus.ERROR, "Could not read the ore-import preview page.");
        }
    }

    public ServiceResult create(ServerPlayer player, String commitToken, String targetProfileId) {
        requireConfigure(player);
        ConfigSnapshot snapshot = DelvefoldConfigService.get().snapshot();
        try {
            ImportContext context = context();
            var attempt = sessions.consumeCommit(
                    player.getUUID(), commitToken, context.binding(), targetProfileId);
            if (!attempt.accepted()) {
                return new ServiceResult(status(attempt.status()), snapshot.ores().revision(),
                        message(attempt.status()), attempt.status() == OreImportSessionService.Status.REVISION_CHANGED);
            }
            var write = DelvefoldConfigService.get().createNewProfile(
                    attempt.targetProfileId(), attempt.plan().proposedProfile());
            if (!write.saved()) {
                return new ServiceResult(ActionStatus.REJECTED, snapshot.ores().revision(),
                        write.message(), true);
            }
            return new ServiceResult(ActionStatus.ACCEPTED, snapshot.ores().revision(),
                    "Created profile '" + attempt.targetProfileId()
                            + "'. It is not active; select it explicitly from Profiles when ready.", true);
        } catch (IOException | IllegalArgumentException exception) {
            return new ServiceResult(ActionStatus.REJECTED, snapshot.ores().revision(),
                    "Profile creation failed: " + exception.getMessage(), true);
        } catch (RuntimeException exception) {
            LOGGER.warn("Ore import profile creation failed for {}", player.getGameProfile().getName(), exception);
            return new ServiceResult(ActionStatus.ERROR, snapshot.ores().revision(),
                    "Profile creation failed; see the server log for details.", false);
        }
    }

    private static ImportContext context() {
        ConfigSnapshot snapshot = DelvefoldConfigService.get().snapshot();
        MinecraftOreImportRegistry.CachedSnapshot registrySnapshot =
                MinecraftOreImportRegistry.cachedSnapshot();
        SnapshotBinding binding = new SnapshotBinding(
                snapshot.ores().revision(),
                registrySnapshot.fingerprint(),
                OreImportFingerprints.profile(snapshot.ores()));
        return new ImportContext(registrySnapshot.registry(), binding);
    }

    private static <T> ViewResult<T> sessionRejected(OreImportSessionService.Status status) {
        return ViewResult.rejected(status(status), message(status));
    }

    private static ActionStatus status(OreImportSessionService.Status status) {
        return status == OreImportSessionService.Status.REVISION_CHANGED
                ? ActionStatus.STALE : ActionStatus.REJECTED;
    }

    private static String message(OreImportSessionService.Status status) {
        return switch (status) {
            case ACCEPTED -> "Ore import session accepted.";
            case RATE_LIMITED -> "Ore discovery is rate limited; wait a moment and try again.";
            case NO_ACTIVE_SCAN, NO_ACTIVE_PREVIEW, INVALID_TOKEN, EXPIRED ->
                    "The ore import preview expired; start a new scan.";
            case REVISION_CHANGED -> "The active ore profile changed; refresh and start a new scan.";
            case REGISTRY_CHANGED -> "Installed blocks or tags changed; start a new scan.";
            case BASE_CHANGED -> "The base profile changed; start a new scan.";
            case INVALID_REQUEST -> "The ore import request was invalid.";
            case PLAN_INVALID -> "The proposed profile has validation errors and cannot be created.";
        };
    }

    private static void requireConfigure(ServerPlayer player) {
        if (player == null || !AdminAccess.canConfigure(player)) {
            throw new SecurityException("Player is not allowed to import Delvefold ore profiles");
        }
    }

    private record ImportContext(MinecraftOreImportRegistry registry, SnapshotBinding binding) {
    }

    public record ViewResult<T>(ActionStatus status, String message, T view) {
        public ViewResult {
            status = Objects.requireNonNull(status, "status");
            message = message == null ? "" : message;
            if ((status == ActionStatus.ACCEPTED) != (view != null)) {
                throw new IllegalArgumentException("Only accepted import results may contain a view");
            }
        }

        static <T> ViewResult<T> accepted(T view) {
            return new ViewResult<>(ActionStatus.ACCEPTED, "", Objects.requireNonNull(view, "view"));
        }

        static <T> ViewResult<T> rejected(ActionStatus status, String message) {
            if (status == ActionStatus.ACCEPTED) {
                throw new IllegalArgumentException("Accepted is not a rejection status");
            }
            return new ViewResult<>(status, message, null);
        }

        public boolean accepted() {
            return status == ActionStatus.ACCEPTED;
        }
    }
}
