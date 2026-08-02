package com.nightsta69.delvefold.admin;

import com.mojang.logging.LogUtils;
import com.nightsta69.delvefold.config.AdminAccess;
import com.nightsta69.delvefold.config.ConfigSnapshot;
import com.nightsta69.delvefold.config.ConfigWriteResult;
import com.nightsta69.delvefold.config.DelvefoldConfigService;
import com.nightsta69.delvefold.config.importer.MinecraftOreImportRegistry;
import com.nightsta69.delvefold.config.importer.OreImportDiscovery;
import com.nightsta69.delvefold.config.importer.OreImportFingerprints;
import com.nightsta69.delvefold.config.importer.OreImportModels;
import com.nightsta69.delvefold.config.importer.OreImportModels.Group;
import com.nightsta69.delvefold.config.importer.OreImportPlanner;
import com.nightsta69.delvefold.config.importer.OreImportSessionService;
import com.nightsta69.delvefold.config.importer.OreImportSessionService.IssuedScan;
import com.nightsta69.delvefold.config.importer.OreImportSessionService.SnapshotBinding;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.validation.ConfigIssueMessages;
import com.nightsta69.delvefold.network.OreLibraryNetworkViews;
import com.nightsta69.delvefold.network.model.ActionStatus;
import com.nightsta69.delvefold.network.model.OreLibraryView;
import com.nightsta69.delvefold.network.payload.AddOreFamiliesPayload;
import com.nightsta69.delvefold.network.payload.OreLibraryRequestPayload;
import com.nightsta69.delvefold.network.service.DelvefoldAdminService.ServiceResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/** Permission-checked server boundary for Unified Ores catalog paging and atomic family additions. */
public final class OreLibraryAdminService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final OreLibraryAdminService INSTANCE = new OreLibraryAdminService(OreImportSessionService.get());

    private final OreImportSessionService sessions;
    private final Object discoveryLock = new Object();
    private volatile @Nullable CachedDiscovery cachedDiscovery;

    OreLibraryAdminService(OreImportSessionService sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    /**
     * Returns the process-wide player-bound ore-library service.
     *
     * @return shared permission-checked library boundary
     */
    public static OreLibraryAdminService get() {
        return INSTANCE;
    }

    /**
     * Opens or pages a server-retained discovery catalog after checking revision, registry, profile, and permission.
     *
     * @param player authorized requesting player
     * @param request bounded page and filter request; a blank token starts a new catalog session
     * @return accepted library page or a bounded localized rejection
     */
    public OreImportAdminService.ViewResult<OreLibraryView> page(
            ServerPlayer player, OreLibraryRequestPayload request) {
        requireConfigure(player);
        ConfigSnapshot snapshot = DelvefoldConfigService.get().snapshot();
        if (snapshot.ores().revision() != request.expectedOreRevision()) {
            return rejected(ActionStatus.STALE, "message.delvefold.ore_library.revision_changed");
        }
        try {
            ImportContext context = context(snapshot);
            IssuedScan scan;
            if (request.catalogToken().isBlank()) {
                var admission = sessions.mayIssueScan(player.getUUID());
                if (!admission.accepted()) {
                    return rejected(ActionStatus.REJECTED, "message.delvefold.import.rate_limited");
                }
                OreImportModels.DiscoveryResult discovery = discovery(context);
                scan = sessions.issueScan(player.getUUID(), context.binding(), discovery);
            } else {
                var access = sessions.accessScan(player.getUUID(), request.catalogToken(), context.binding());
                if (!access.accepted()) {
                    return sessionRejected(access.status());
                }
                scan = Objects.requireNonNull(access.scan(), "accepted ore-library scan");
            }
            OreLibraryView view = OreLibraryNetworkViews.page(
                    scan.scanToken(),
                    scan.discovery(),
                    snapshot.ores(),
                    context.registry(),
                    request.query(),
                    request.showConfigured(),
                    request.page());
            return OreImportAdminService.ViewResult.accepted(view);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Unified Ores library failed for {}",
                    player.getGameProfile().getName(),
                    exception);
            return rejected(ActionStatus.ERROR, "message.delvefold.ore_library.failed");
        }
    }

    /**
     * Resolves selected family IDs against their exact retained catalog and commits one validated active-profile write.
     *
     * @param player authorized acting player
     * @param request bounded catalog token, expected revision, and distinct family IDs
     * @return one atomic mutation result; every rejection leaves the active profile unchanged
     */
    public ServiceResult addFamilies(ServerPlayer player, AddOreFamiliesPayload request) {
        requireConfigure(player);
        ConfigSnapshot snapshot = DelvefoldConfigService.get().snapshot();
        if (snapshot.ores().revision() != request.expectedRevision()) {
            return result(
                    ActionStatus.STALE,
                    snapshot.ores().revision(),
                    "message.delvefold.ore_library.revision_changed",
                    true);
        }
        try {
            ImportContext context = context(snapshot);
            var access = sessions.accessScan(player.getUUID(), request.catalogToken(), context.binding());
            if (!access.accepted()) {
                return sessionResult(access.status(), snapshot.ores().revision());
            }
            IssuedScan scan = Objects.requireNonNull(access.scan(), "accepted ore-library scan");
            Map<String, Group> available = new LinkedHashMap<>();
            scan.discovery().groups().forEach(group -> available.put(group.id(), group));
            List<Group> selected =
                    request.familyIds().stream().map(available::get).toList();
            if (selected.stream().anyMatch(Objects::isNull)) {
                return result(
                        ActionStatus.REJECTED,
                        snapshot.ores().revision(),
                        "message.delvefold.ore_library.selection_unknown",
                        false);
            }
            OreImportModels.Plan plan = OreImportPlanner.plan(
                    snapshot.ores(), selected, scan.discovery().groups(), context.registry(), context.registry());
            if (!plan.valid()) {
                String issue = plan.validation().issues().stream()
                        .map(ConfigIssueMessages::encode)
                        .findFirst()
                        .orElse(localized("message.delvefold.ore_library.validation_failed"));
                return new ServiceResult(ActionStatus.REJECTED, snapshot.ores().revision(), issue, false);
            }
            if (plan.addedRuleCount() == 0L) {
                return result(
                        ActionStatus.REJECTED,
                        snapshot.ores().revision(),
                        "message.delvefold.ore_library.nothing_added",
                        false);
            }

            ConfigWriteResult write = DelvefoldConfigService.get()
                    .updateOres(
                            request.expectedRevision(),
                            active -> new OreProfileDocument(
                                    OreProfileDocument.CURRENT_SCHEMA_VERSION,
                                    active.revision(),
                                    active.profile(),
                                    plan.proposedProfile().rules()));
            ConfigSnapshot resulting = write.snapshot();
            long revision = resulting == null
                    ? snapshot.ores().revision()
                    : resulting.ores().revision();
            if (!write.saved()) {
                String issue = write.issues().stream()
                        .map(ConfigIssueMessages::encode)
                        .findFirst()
                        .orElse(localized("message.delvefold.ore_library.validation_failed"));
                ActionStatus status =
                        revision == request.expectedRevision() ? ActionStatus.REJECTED : ActionStatus.STALE;
                return new ServiceResult(status, revision, issue, status == ActionStatus.STALE);
            }
            sessions.invalidatePlayer(player.getUUID());
            return new ServiceResult(
                    ActionStatus.ACCEPTED,
                    revision,
                    localized("message.delvefold.ore_library.added", plan.addedRuleCount()),
                    true);
        } catch (IllegalArgumentException exception) {
            return new ServiceResult(
                    ActionStatus.REJECTED,
                    snapshot.ores().revision(),
                    localized("message.delvefold.ore_library.rejected", exception.getMessage()),
                    false);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Unified Ores batch failed for {}", player.getGameProfile().getName(), exception);
            return result(
                    ActionStatus.ERROR,
                    snapshot.ores().revision(),
                    "message.delvefold.ore_library.internal_error",
                    false);
        }
    }

    private static ImportContext context(ConfigSnapshot snapshot) {
        MinecraftOreImportRegistry.CachedSnapshot registrySnapshot = MinecraftOreImportRegistry.cachedSnapshot();
        SnapshotBinding binding = new SnapshotBinding(
                snapshot.ores().revision(),
                registrySnapshot.fingerprint(),
                OreImportFingerprints.profile(snapshot.ores()));
        return new ImportContext(registrySnapshot.registry(), binding);
    }

    private OreImportModels.DiscoveryResult discovery(ImportContext context) {
        String fingerprint = context.binding().registryFingerprint();
        CachedDiscovery current = this.cachedDiscovery;
        if (current != null && current.registryFingerprint().equals(fingerprint)) {
            return current.discovery();
        }
        synchronized (this.discoveryLock) {
            current = this.cachedDiscovery;
            if (current == null || !current.registryFingerprint().equals(fingerprint)) {
                current = new CachedDiscovery(
                        fingerprint,
                        OreImportDiscovery.discover(
                                context.registry(), OreImportModels.DiscoveryOptions.INCLUDING_VANILLA));
                this.cachedDiscovery = current;
            }
            return current.discovery();
        }
    }

    private static OreImportAdminService.ViewResult<OreLibraryView> sessionRejected(
            OreImportSessionService.Status status) {
        ActionStatus action =
                status == OreImportSessionService.Status.REVISION_CHANGED ? ActionStatus.STALE : ActionStatus.REJECTED;
        return OreImportAdminService.ViewResult.rejected(action, sessionMessage(status));
    }

    private static ServiceResult sessionResult(OreImportSessionService.Status status, long revision) {
        ActionStatus action =
                status == OreImportSessionService.Status.REVISION_CHANGED ? ActionStatus.STALE : ActionStatus.REJECTED;
        return new ServiceResult(action, revision, sessionMessage(status), action == ActionStatus.STALE);
    }

    private static String sessionMessage(OreImportSessionService.Status status) {
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

    private static OreImportAdminService.ViewResult<OreLibraryView> rejected(
            ActionStatus status, String translationKey) {
        return OreImportAdminService.ViewResult.rejected(status, localized(translationKey));
    }

    private static ServiceResult result(ActionStatus status, long revision, String translationKey, boolean refresh) {
        return new ServiceResult(status, revision, localized(translationKey), refresh);
    }

    private static String localized(String translationKey, @Nullable Object... arguments) {
        return AdminLocalizedMessage.encode(translationKey, arguments);
    }

    private static void requireConfigure(ServerPlayer player) {
        if (player == null || !AdminAccess.canConfigure(player)) {
            throw new SecurityException("Player is not allowed to manage the Unified Ores library");
        }
    }

    private record ImportContext(MinecraftOreImportRegistry registry, SnapshotBinding binding) {}

    private record CachedDiscovery(String registryFingerprint, OreImportModels.DiscoveryResult discovery) {}
}
