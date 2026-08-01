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
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/** Permission-checked server boundary for scan, preview, paging, and strict profile creation. */
public final class OreImportAdminService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final OreImportAdminService INSTANCE = new OreImportAdminService(OreImportSessionService.get());

    private final OreImportSessionService sessions;

    OreImportAdminService(OreImportSessionService sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    /**
     * Returns the process-wide import boundary.
     *
     * @return the service whose sessions are bound to server players and configuration revisions
     */
    public static OreImportAdminService get() {
        return INSTANCE;
    }

    /**
     * Discovers registered ore-like blocks and creates a player-bound, expiring scan session.
     *
     * @param player the requesting server player, who must hold configuration permission
     * @param expectedOreRevision the ore revision displayed by the requesting client
     * @param includeVanilla whether vanilla ore families are included in discovery
     * @return an accepted first page or a bounded rejection suitable for the administration protocol
     * @throws SecurityException if the player lacks configuration permission
     */
    public ViewResult<ScanView> scan(ServerPlayer player, long expectedOreRevision, boolean includeVanilla) {
        requireConfigure(player);
        ConfigSnapshot snapshot = DelvefoldConfigService.get().snapshot();
        if (snapshot.ores().revision() != expectedOreRevision) {
            return ViewResult.rejected(ActionStatus.STALE, localized("message.delvefold.import.scan.revision_changed"));
        }
        var admission = sessions.mayIssueScan(player.getUUID());
        if (!admission.accepted()) {
            return ViewResult.rejected(ActionStatus.REJECTED, localized("message.delvefold.import.rate_limited"));
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
            return ViewResult.rejected(ActionStatus.ERROR, localized("message.delvefold.import.scan.failed"));
        }
    }

    /**
     * Reads one bounded page from an existing player- and revision-bound scan.
     *
     * @param player the requesting server player, who must own the session
     * @param scanToken the opaque scan capability issued to that player
     * @param page the zero-based page index; the network view clamps it to the available range
     * @return an accepted scan page or a rejection when the session, token, registry, or revision is stale
     * @throws SecurityException if the player lacks configuration permission
     */
    public ViewResult<ScanView> scanPage(ServerPlayer player, String scanToken, int page) {
        requireConfigure(player);
        try {
            ImportContext context = context();
            var access = sessions.accessScan(player.getUUID(), scanToken, context.binding());
            if (!access.accepted()) {
                return sessionRejected(access.status());
            }
            ConfigSnapshot snapshot = DelvefoldConfigService.get().snapshot();
            var scan = Objects.requireNonNull(access.scan(), "accepted scan access");
            return ViewResult.accepted(OreImportNetworkViews.scan(
                    scan, snapshot.ores().revision(), snapshot.ores().profile(), page));
        } catch (RuntimeException exception) {
            return ViewResult.rejected(ActionStatus.ERROR, localized("message.delvefold.import.scan_page.failed"));
        }
    }

    /**
     * Builds a read-only diff and workload preview for selected discovery groups.
     *
     * @param player the requesting server player, who must own the scan session
     * @param scanToken the opaque scan capability issued to that player
     * @param selectedGroupIds immutable-by-convention discovery group identifiers to include
     * @return an accepted first preview page and commit capability, or a bounded rejection
     * @throws SecurityException if the player lacks configuration permission
     */
    public ViewResult<PreviewView> preview(ServerPlayer player, String scanToken, List<String> selectedGroupIds) {
        requireConfigure(player);
        try {
            ImportContext context = context();
            var access = sessions.accessScan(player.getUUID(), scanToken, context.binding());
            if (!access.accepted()) {
                return sessionRejected(access.status());
            }
            var scan = Objects.requireNonNull(access.scan(), "accepted scan access");
            Map<String, OreImportModels.Group> available = new LinkedHashMap<>();
            scan.discovery().groups().forEach(group -> available.put(group.id(), group));
            List<OreImportModels.Group> selected =
                    selectedGroupIds.stream().map(available::get).toList();
            if (selected.stream().anyMatch(Objects::isNull)) {
                return ViewResult.rejected(
                        ActionStatus.REJECTED, localized("message.delvefold.import.selection_unknown"));
            }
            ConfigSnapshot snapshot = DelvefoldConfigService.get().snapshot();
            OreImportModels.Plan plan =
                    OreImportPlanner.plan(snapshot.ores(), selected, context.registry(), context.registry());
            PreviewRequest request = new PreviewRequest(snapshot.ores().profile(), selectedGroupIds);
            var issue = sessions.issuePreview(player.getUUID(), scanToken, context.binding(), request, plan);
            if (!issue.accepted()) {
                return sessionRejected(issue.status());
            }
            var preview = Objects.requireNonNull(issue.preview(), "accepted preview issue");
            return ViewResult.accepted(
                    OreImportNetworkViews.preview(preview, snapshot.ores().revision(), 0));
        } catch (IllegalArgumentException exception) {
            return ViewResult.rejected(ActionStatus.REJECTED, exception.getMessage());
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Ore import preview failed for {}", player.getGameProfile().getName(), exception);
            return ViewResult.rejected(ActionStatus.ERROR, localized("message.delvefold.import.preview.failed"));
        }
    }

    /**
     * Reads one bounded page from an existing import preview without changing configuration.
     *
     * @param player the requesting server player, who must own the preview session
     * @param commitToken the opaque preview capability bound to player, registry, and base profile
     * @param page the zero-based page index; the network view clamps it to the available range
     * @return an accepted preview page or a rejection when its capability is no longer valid
     * @throws SecurityException if the player lacks configuration permission
     */
    public ViewResult<PreviewView> previewPage(ServerPlayer player, String commitToken, int page) {
        requireConfigure(player);
        try {
            ImportContext context = context();
            var access = sessions.accessPreview(player.getUUID(), commitToken, context.binding());
            if (!access.accepted()) {
                return sessionRejected(access.status());
            }
            var preview = Objects.requireNonNull(access.preview(), "accepted preview access");
            return ViewResult.accepted(OreImportNetworkViews.preview(
                    preview, DelvefoldConfigService.get().snapshot().ores().revision(), page));
        } catch (RuntimeException exception) {
            return ViewResult.rejected(ActionStatus.ERROR, localized("message.delvefold.import.preview_page.failed"));
        }
    }

    /**
     * Consumes an accepted preview capability and writes its proposal as a new inactive profile.
     *
     * <p>The operation never overwrites or activates a profile. The capability is consumed before persistence so it
     * cannot be replayed.
     *
     * @param player the requesting server player, who must own the preview session
     * @param commitToken the one-use preview capability
     * @param targetProfileId the validated identifier for the new profile
     * @return the resulting ore revision, localized status, and refresh requirement
     * @throws SecurityException if the player lacks configuration permission
     */
    public ServiceResult create(ServerPlayer player, String commitToken, String targetProfileId) {
        requireConfigure(player);
        ConfigSnapshot snapshot = DelvefoldConfigService.get().snapshot();
        try {
            ImportContext context = context();
            var attempt = sessions.consumeCommit(player.getUUID(), commitToken, context.binding(), targetProfileId);
            if (!attempt.accepted()) {
                return new ServiceResult(
                        status(attempt.status()),
                        snapshot.ores().revision(),
                        message(attempt.status()),
                        attempt.status() == OreImportSessionService.Status.REVISION_CHANGED);
            }
            String acceptedTarget = Objects.requireNonNull(attempt.targetProfileId(), "accepted import target");
            OreImportModels.Plan acceptedPlan = Objects.requireNonNull(attempt.plan(), "accepted import plan");
            var write = DelvefoldConfigService.get().createNewProfile(acceptedTarget, acceptedPlan.proposedProfile());
            if (!write.saved()) {
                return new ServiceResult(ActionStatus.REJECTED, snapshot.ores().revision(), write.message(), true);
            }
            return new ServiceResult(
                    ActionStatus.ACCEPTED,
                    snapshot.ores().revision(),
                    localized("message.delvefold.import.profile_created", acceptedTarget),
                    true);
        } catch (IOException | IllegalArgumentException exception) {
            return new ServiceResult(
                    ActionStatus.REJECTED,
                    snapshot.ores().revision(),
                    localized("message.delvefold.import.profile_failed", exception.getMessage()),
                    true);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Ore import profile creation failed for {}",
                    player.getGameProfile().getName(),
                    exception);
            return new ServiceResult(
                    ActionStatus.ERROR,
                    snapshot.ores().revision(),
                    localized("message.delvefold.import.profile_internal_error"),
                    false);
        }
    }

    private static ImportContext context() {
        ConfigSnapshot snapshot = DelvefoldConfigService.get().snapshot();
        MinecraftOreImportRegistry.CachedSnapshot registrySnapshot = MinecraftOreImportRegistry.cachedSnapshot();
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
        return status == OreImportSessionService.Status.REVISION_CHANGED ? ActionStatus.STALE : ActionStatus.REJECTED;
    }

    private static String message(OreImportSessionService.Status status) {
        return switch (status) {
            case ACCEPTED -> localized("message.delvefold.import.session.accepted");
            case RATE_LIMITED -> localized("message.delvefold.import.rate_limited");
            case NO_ACTIVE_SCAN, NO_ACTIVE_PREVIEW, INVALID_TOKEN, EXPIRED ->
                localized("message.delvefold.import.session.expired");
            case REVISION_CHANGED -> localized("message.delvefold.import.session.revision_changed");
            case REGISTRY_CHANGED -> localized("message.delvefold.import.session.registry_changed");
            case BASE_CHANGED -> localized("message.delvefold.import.session.base_changed");
            case INVALID_REQUEST -> localized("message.delvefold.import.session.invalid_request");
            case PLAN_INVALID -> localized("message.delvefold.import.session.plan_invalid");
        };
    }

    private static String localized(String translationKey, @Nullable Object... arguments) {
        return AdminLocalizedMessage.encode(translationKey, arguments);
    }

    private static void requireConfigure(ServerPlayer player) {
        if (player == null || !AdminAccess.canConfigure(player)) {
            throw new SecurityException("Player is not allowed to import Delvefold ore profiles");
        }
    }

    private record ImportContext(MinecraftOreImportRegistry registry, SnapshotBinding binding) {}

    /**
     * Couples an administrative status with the view present only for accepted requests.
     *
     * @param <T> bounded network-view type
     * @param status accepted, stale, rejected, or internal-error status
     * @param message encoded localized message; empty for accepted results
     * @param view accepted result body, or {@code null} for every non-accepted status
     */
    public record ViewResult<T>(
            ActionStatus status, String message, @Nullable T view) {
        /**
         * Validates the invariant that accepted status and view presence are equivalent.
         *
         * @param status accepted, stale, rejected, or internal-error status
         * @param message encoded localized message; {@code null} is normalized to empty
         * @param view accepted result body, or {@code null} for every non-accepted status
         */
        public ViewResult {
            Objects.requireNonNull(status, "status");
            message = message == null ? "" : message;
            if ((status == ActionStatus.ACCEPTED) != (view != null)) {
                throw new IllegalArgumentException("Only accepted import results may contain a view");
            }
        }

        static <T> ViewResult<T> accepted(T view) {
            return new ViewResult<>(ActionStatus.ACCEPTED, "", Objects.requireNonNull(view, "view"));
        }

        static <T> ViewResult<T> rejected(ActionStatus status, @Nullable String message) {
            if (status == ActionStatus.ACCEPTED) {
                throw new IllegalArgumentException("Accepted is not a rejection status");
            }
            return new ViewResult<>(status, message == null ? "" : message, null);
        }

        /**
         * Reports whether this result carries a non-null view.
         *
         * @return {@code true} exactly when {@link #status()} is {@link ActionStatus#ACCEPTED}
         */
        public boolean accepted() {
            return status == ActionStatus.ACCEPTED;
        }
    }
}
