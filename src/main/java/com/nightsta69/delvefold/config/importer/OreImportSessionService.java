package com.nightsta69.delvefold.config.importer;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Owns the short-lived, server-authoritative state for the guided ore importer.
 *
 * <p>Only opaque random tokens leave this service. Discovery results and plans stay server-side and are bound to their
 * owner UUID and the exact configuration and registry fingerprints from which they were produced. Session mutation is
 * serialized under one lock. Tokens contain 256 random bits, are stored only as SHA-256 digests, are compared in
 * constant time when well formed, and never grant permission by themselves; callers must still perform the surrounding
 * authorization. Expiry arithmetic saturates at {@link Long#MAX_VALUE} rather than wrapping.
 */
public final class OreImportSessionService {
    /** Lifetime of scan admissions, issued scans, and preview commit tokens. */
    public static final Duration SESSION_TTL = Duration.ofMinutes(5);
    /** Minimum interval between accepted registry-scan requests from one owner UUID. */
    public static final Duration SCAN_COOLDOWN = Duration.ofSeconds(2);

    private static final OreImportSessionService INSTANCE =
            new OreImportSessionService(Clock.systemUTC(), OreImportTokenSecurity.secureTokenSource());

    private final Object lock = new Object();
    private final Clock clock;
    private final TokenSource tokens;
    private final Map<UUID, ScanSession> scans = new HashMap<>();
    private final Map<UUID, PreviewSession> previews = new HashMap<>();
    private final Map<UUID, Long> lastScanRequests = new HashMap<>();
    private final Map<UUID, Long> scanAdmissions = new HashMap<>();

    /** Creates an isolated session store using the system UTC clock and a cryptographically secure token source. */
    public OreImportSessionService() {
        this(Clock.systemUTC(), OreImportTokenSecurity.secureTokenSource());
    }

    OreImportSessionService(Clock clock, TokenSource tokens) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.tokens = Objects.requireNonNull(tokens, "tokens");
    }

    /**
     * Returns the process-wide server session store.
     *
     * @return shared thread-safe ore-import session service
     */
    public static OreImportSessionService get() {
        return INSTANCE;
    }

    /**
     * Atomically rate-limits and reserves one registry scan for a player. Callers must receive an accepted result
     * before doing discovery and then complete the reservation through {@link #issueScan(UUID, SnapshotBinding,
     * OreImportModels.DiscoveryResult)}.
     *
     * @param owner UUID of the authorized player requesting discovery
     * @return accepted admission with its timestamp, or a rate-limited result with the earliest retry timestamp
     */
    public ScanAdmission mayIssueScan(UUID owner) {
        Objects.requireNonNull(owner, "owner");
        synchronized (lock) {
            long now = clock.millis();
            Long previous = lastScanRequests.get(owner);
            long retryAt = previous == null ? now : OreImportSessionValidation.expiresAt(previous, SCAN_COOLDOWN);
            if (previous != null && now < retryAt) {
                return ScanAdmission.rejected(retryAt);
            }
            lastScanRequests.put(owner, now);
            scanAdmissions.put(owner, now);
            return ScanAdmission.accepted(now);
        }
    }

    /**
     * Starts a fresh scan and invalidates every older scan or preview token owned by the player.
     *
     * <p>The owner must hold a non-expired admission returned by {@link #mayIssueScan(UUID)}. A new opaque token and
     * full {@link #SESSION_TTL} are issued without exposing the server-side fingerprint binding.
     *
     * @param owner UUID that owns the prior scan admission and resulting session
     * @param binding ore revision, registry fingerprint, and base-profile fingerprint captured for discovery
     * @param discovery immutable bounded discovery result retained server-side
     * @return issued scan view containing the one-owner token, expiry timestamp, and discovery result
     * @throws IllegalStateException when no valid scan admission exists or a unique secure token cannot be issued
     */
    public IssuedScan issueScan(UUID owner, SnapshotBinding binding, OreImportModels.DiscoveryResult discovery) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(discovery, "discovery");
        synchronized (lock) {
            long now = clock.millis();
            Long admittedAt = scanAdmissions.remove(owner);
            if (!OreImportSessionValidation.validAdmission(admittedAt, now, SESSION_TTL)) {
                throw new IllegalStateException("Ore-import discovery was not admitted or its admission expired");
            }
            long expiresAt = OreImportSessionValidation.expiresAt(now, SESSION_TTL);
            Token token = issueUniqueToken();
            scans.put(owner, new ScanSession(token.digest(), expiresAt, binding, discovery));
            previews.remove(owner);
            return new IssuedScan(token.value(), expiresAt, discovery);
        }
    }

    /**
     * Reads an admitted scan for paging without consuming its token or extending its expiry.
     *
     * <p>Invalid tokens do not consume state. Expired scans are removed. A revision, registry, or base-profile mismatch
     * invalidates both scan and preview state for the owner.
     *
     * @param owner UUID that owns the scan session
     * @param scanToken opaque scan token, or {@code null} for an invalid-token result
     * @param current current server-side revision and fingerprints to compare with the captured binding
     * @return accepted scan view, or a rejected access with a specific status and {@code null} scan
     */
    public ScanAccess accessScan(UUID owner, @Nullable String scanToken, SnapshotBinding current) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(current, "current");
        synchronized (lock) {
            ScanSession scan = scans.get(owner);
            if (scan == null) {
                return ScanAccess.rejected(Status.NO_ACTIVE_SCAN);
            }
            long now = clock.millis();
            if (OreImportSessionValidation.expired(scan.expiresAtEpochMillis(), now)) {
                scans.remove(owner);
                return ScanAccess.rejected(Status.EXPIRED);
            }
            if (!OreImportTokenSecurity.tokenMatches(scan.tokenDigest(), scanToken)) {
                return ScanAccess.rejected(Status.INVALID_TOKEN);
            }
            Status state = OreImportSessionValidation.compareBindings(scan.binding(), current);
            if (state != Status.ACCEPTED) {
                scans.remove(owner);
                previews.remove(owner);
                return ScanAccess.rejected(state);
            }
            String acceptedToken = Objects.requireNonNull(scanToken, "validated scan token");
            return ScanAccess.accepted(new IssuedScan(acceptedToken, scan.expiresAtEpochMillis(), scan.discovery()));
        }
    }

    /**
     * Creates or replaces a preview after verifying its scan token and current server state. Invalid previews may still
     * be viewed, but their commit token is rejected as {@link Status#PLAN_INVALID}.
     *
     * <p>This method does not save, overwrite, or activate a profile. The scan remains readable and the new preview
     * replaces only the owner's prior preview.
     *
     * @param owner UUID that owns the admitted scan
     * @param scanToken opaque scan token, or {@code null} for an invalid-token result
     * @param current current server-side revision and fingerprints
     * @param request selected groups and base profile asserted by the client request
     * @param plan server-built immutable preview plan to bind to the commit token
     * @return accepted issued preview, or a rejection with a {@code null} preview
     */
    public PreviewIssue issuePreview(
            UUID owner,
            @Nullable String scanToken,
            SnapshotBinding current,
            PreviewRequest request,
            OreImportModels.Plan plan) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(plan, "plan");
        synchronized (lock) {
            ScanSession scan = scans.get(owner);
            if (scan == null) {
                return PreviewIssue.rejected(Status.NO_ACTIVE_SCAN);
            }
            long now = clock.millis();
            if (OreImportSessionValidation.expired(scan.expiresAtEpochMillis(), now)) {
                scans.remove(owner);
                return PreviewIssue.rejected(Status.EXPIRED);
            }
            if (!OreImportTokenSecurity.tokenMatches(scan.tokenDigest(), scanToken)) {
                return PreviewIssue.rejected(Status.INVALID_TOKEN);
            }
            Status state = OreImportSessionValidation.compareBindings(scan.binding(), current);
            if (state != Status.ACCEPTED) {
                scans.remove(owner);
                previews.remove(owner);
                return PreviewIssue.rejected(state);
            }
            if (!OreImportSessionValidation.validRequest(scan.discovery(), request, plan)) {
                return PreviewIssue.rejected(Status.INVALID_REQUEST);
            }

            Token commitToken = issueUniqueToken();
            long expiresAt = OreImportSessionValidation.expiresAt(now, SESSION_TTL);
            PreviewSession preview = new PreviewSession(commitToken.digest(), expiresAt, scan.binding(), request, plan);
            previews.put(owner, preview);
            return PreviewIssue.accepted(new IssuedPreview(commitToken.value(), expiresAt, request, plan));
        }
    }

    /**
     * Consumes a matching commit token before checking mutable server state. Consequently stale, invalid, and failed
     * commit attempts cannot replay the token.
     *
     * <p>An unrecognized or malformed token does not consume state. A recognized token removes both the preview and
     * scan before checking fingerprints, target-profile bounds, and plan validity. Acceptance authorizes the caller to
     * attempt a later create-only save; this service itself never saves, overwrites, or activates the proposed profile.
     *
     * @param owner UUID that owns the preview
     * @param commitToken opaque one-use commit token, or {@code null} for an invalid-token result
     * @param current current server-side revision and fingerprints
     * @param targetProfileId new profile ID requested for a later create-only save, or {@code null}
     * @return accepted request/plan/target tuple, or a rejection whose nullable tuple components are all {@code null}
     */
    public CommitAttempt consumeCommit(
            UUID owner, @Nullable String commitToken, SnapshotBinding current, @Nullable String targetProfileId) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(current, "current");
        synchronized (lock) {
            PreviewSession preview = previews.get(owner);
            if (preview == null) {
                return CommitAttempt.rejected(Status.NO_ACTIVE_PREVIEW);
            }
            long now = clock.millis();
            if (OreImportSessionValidation.expired(preview.expiresAtEpochMillis(), now)) {
                previews.remove(owner);
                scans.remove(owner);
                return CommitAttempt.rejected(Status.EXPIRED);
            }
            if (!OreImportTokenSecurity.tokenMatches(preview.tokenDigest(), commitToken)) {
                return CommitAttempt.rejected(Status.INVALID_TOKEN);
            }

            // A recognized commit token is one-use even when a later check rejects the operation.
            previews.remove(owner);
            scans.remove(owner);

            Status state = OreImportSessionValidation.compareBindings(preview.binding(), current);
            if (state != Status.ACCEPTED) {
                return CommitAttempt.rejected(state);
            }
            String target = OreImportSessionValidation.normalizedProfileId(targetProfileId);
            if (target == null) {
                return CommitAttempt.rejected(Status.INVALID_REQUEST);
            }
            if (!preview.plan().valid()) {
                return CommitAttempt.rejected(Status.PLAN_INVALID);
            }
            return CommitAttempt.accepted(preview.request(), preview.plan(), target);
        }
    }

    /**
     * Reads a preview for paging without consuming its one-use commit token or extending its expiry.
     *
     * <p>Invalid tokens leave state intact. Expiry or fingerprint drift removes both the scan and preview owned by the
     * player.
     *
     * @param owner UUID that owns the preview session
     * @param commitToken opaque commit token, or {@code null} for an invalid-token result
     * @param current current server-side revision and fingerprints
     * @return accepted preview view, or a rejected access with a specific status and {@code null} preview
     */
    public PreviewAccess accessPreview(UUID owner, @Nullable String commitToken, SnapshotBinding current) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(current, "current");
        synchronized (lock) {
            PreviewSession preview = previews.get(owner);
            if (preview == null) {
                return PreviewAccess.rejected(Status.NO_ACTIVE_PREVIEW);
            }
            long now = clock.millis();
            if (OreImportSessionValidation.expired(preview.expiresAtEpochMillis(), now)) {
                previews.remove(owner);
                scans.remove(owner);
                return PreviewAccess.rejected(Status.EXPIRED);
            }
            if (!OreImportTokenSecurity.tokenMatches(preview.tokenDigest(), commitToken)) {
                return PreviewAccess.rejected(Status.INVALID_TOKEN);
            }
            Status state = OreImportSessionValidation.compareBindings(preview.binding(), current);
            if (state != Status.ACCEPTED) {
                previews.remove(owner);
                scans.remove(owner);
                return PreviewAccess.rejected(state);
            }
            String acceptedToken = Objects.requireNonNull(commitToken, "validated commit token");
            return PreviewAccess.accepted(new IssuedPreview(
                    acceptedToken, preview.expiresAtEpochMillis(), preview.request(), preview.plan()));
        }
    }

    /**
     * Invalidates all scan, preview, cooldown, and outstanding-admission state for one owner.
     *
     * @param owner player UUID to invalidate, or {@code null} for no action
     */
    public void invalidatePlayer(@Nullable UUID owner) {
        if (owner == null) {
            return;
        }
        synchronized (lock) {
            scans.remove(owner);
            previews.remove(owner);
            lastScanRequests.remove(owner);
            scanAdmissions.remove(owner);
        }
    }

    /** Invalidates every owner's scan, preview, cooldown, and outstanding-admission state. */
    public void invalidateAll() {
        synchronized (lock) {
            scans.clear();
            previews.clear();
            lastScanRequests.clear();
            scanAdmissions.clear();
        }
    }

    private Token issueUniqueToken() {
        OreImportTokenSecurity.IssuedToken issued = OreImportTokenSecurity.issueUniqueToken(tokens, this::tokenInUse);
        return new Token(issued.value(), issued.digest());
    }

    private boolean tokenInUse(byte[] candidate) {
        for (ScanSession session : scans.values()) {
            if (OreImportTokenSecurity.digestsEqual(session.tokenDigest(), candidate)) {
                return true;
            }
        }
        for (PreviewSession session : previews.values()) {
            if (OreImportTokenSecurity.digestsEqual(session.tokenDigest(), candidate)) {
                return true;
            }
        }
        return false;
    }

    @FunctionalInterface
    interface TokenSource {
        String nextToken();
    }

    /**
     * Immutable stale-state binding captured when registry discovery begins.
     *
     * @param expectedOreRevision exact non-negative ore-profile revision
     * @param registryFingerprint deterministic fingerprint of installed blocks and block-tag membership
     * @param baseContentHash deterministic fingerprint of the source profile's canonical JSON
     */
    public record SnapshotBinding(long expectedOreRevision, String registryFingerprint, String baseContentHash) {
        /**
         * Validates the revision and normalizes both non-empty bounded fingerprint strings.
         *
         * @param expectedOreRevision exact non-negative ore-profile revision
         * @param registryFingerprint deterministic registry fingerprint
         * @param baseContentHash deterministic source-profile fingerprint
         */
        public SnapshotBinding {
            if (expectedOreRevision < 0L) {
                throw new IllegalArgumentException("Expected ore revision cannot be negative");
            }
            registryFingerprint =
                    OreImportSessionValidation.normalizedStateKey(registryFingerprint, "registryFingerprint");
            baseContentHash = OreImportSessionValidation.normalizedStateKey(baseContentHash, "baseContentHash");
        }
    }

    /**
     * Bounded client selection that is revalidated against server-retained discovery and planning results.
     *
     * @param baseProfileId source profile identifier used to create the plan
     * @param selectedGroupIds one or more unique discovery group IDs retained in request order
     */
    public record PreviewRequest(String baseProfileId, List<String> selectedGroupIds) {
        /**
         * Trims the profile and group IDs, requires a bounded non-empty unique selection, and stores an immutable list.
         *
         * @param baseProfileId source profile identifier
         * @param selectedGroupIds selected discovery group IDs
         */
        public PreviewRequest {
            baseProfileId = OreImportSessionValidation.normalizedBaseProfileId(baseProfileId);
            selectedGroupIds = OreImportSessionValidation.normalizedSelectedGroupIds(selectedGroupIds);
        }
    }

    /**
     * Owner-scoped scan view returned to an authorized caller.
     *
     * @param scanToken opaque token whose digest is retained server-side
     * @param expiresAtEpochMillis absolute UTC epoch-millisecond expiry; access does not extend it
     * @param discovery immutable bounded discovery result retained by the matching server session
     */
    public record IssuedScan(String scanToken, long expiresAtEpochMillis, OreImportModels.DiscoveryResult discovery) {}

    /**
     * Owner-scoped preview view returned after a scan token and plan request are validated.
     *
     * @param commitToken opaque one-use token whose digest is retained server-side
     * @param expiresAtEpochMillis absolute UTC epoch-millisecond expiry; preview access does not extend it
     * @param request immutable group selection bound to the token
     * @param plan immutable server-built plan bound to the token
     */
    public record IssuedPreview(
            String commitToken, long expiresAtEpochMillis, PreviewRequest request, OreImportModels.Plan plan) {}

    /**
     * Result of atomically applying the per-owner registry-scan cooldown.
     *
     * @param status {@link Status#ACCEPTED} or {@link Status#RATE_LIMITED}
     * @param admittedAtEpochMillis admission timestamp when accepted, otherwise zero
     * @param retryAtEpochMillis earliest retry timestamp when rate-limited, or the admission timestamp when accepted
     */
    public record ScanAdmission(Status status, long admittedAtEpochMillis, long retryAtEpochMillis) {
        /**
         * Enforces the two statuses valid for scan admission.
         *
         * @param status accepted or rate-limited status
         * @param admittedAtEpochMillis admission timestamp or zero
         * @param retryAtEpochMillis earliest retry timestamp
         */
        public ScanAdmission {
            Objects.requireNonNull(status, "status");
            if (status != Status.ACCEPTED && status != Status.RATE_LIMITED) {
                throw new IllegalArgumentException("Scan admission requires an accepted or rate-limited status");
            }
        }

        /**
         * Creates an accepted admission reserved at the supplied timestamp.
         *
         * @param admittedAtEpochMillis UTC epoch-millisecond admission timestamp
         * @return accepted admission whose retry timestamp equals its admission timestamp
         */
        public static ScanAdmission accepted(long admittedAtEpochMillis) {
            return new ScanAdmission(Status.ACCEPTED, admittedAtEpochMillis, admittedAtEpochMillis);
        }

        /**
         * Creates a rate-limited admission result.
         *
         * @param retryAtEpochMillis earliest UTC epoch-millisecond retry timestamp
         * @return rejected admission with no admission timestamp
         */
        public static ScanAdmission rejected(long retryAtEpochMillis) {
            return new ScanAdmission(Status.RATE_LIMITED, 0L, retryAtEpochMillis);
        }

        /**
         * Reports whether registry discovery was admitted and reserved.
         *
         * @return {@code true} only for {@link Status#ACCEPTED}
         */
        public boolean accepted() {
            return status == Status.ACCEPTED;
        }
    }

    /**
     * Read-only access result for an existing scan.
     *
     * @param status access outcome
     * @param scan issued scan when accepted, or {@code null} for every rejection
     */
    public record ScanAccess(Status status, @Nullable IssuedScan scan) {
        /**
         * Enforces that accepted access has a scan and rejected access has no scan.
         *
         * @param status access outcome
         * @param scan issued scan for acceptance, otherwise {@code null}
         */
        public ScanAccess {
            Objects.requireNonNull(status, "status");
            if ((status == Status.ACCEPTED) != (scan != null)) {
                throw new IllegalArgumentException("Only accepted scan access may contain scan state");
            }
        }

        /**
         * Creates accepted read-only scan access.
         *
         * @param scan non-null issued scan view
         * @return accepted access containing {@code scan}
         */
        public static ScanAccess accepted(IssuedScan scan) {
            return new ScanAccess(Status.ACCEPTED, Objects.requireNonNull(scan, "scan"));
        }

        /**
         * Creates rejected scan access without exposing session state.
         *
         * @param status any status other than {@link Status#ACCEPTED}
         * @return rejected access with a {@code null} scan
         */
        public static ScanAccess rejected(Status status) {
            if (status == Status.ACCEPTED) {
                throw new IllegalArgumentException("Accepted is not a rejection status");
            }
            return new ScanAccess(status, null);
        }

        /**
         * Reports whether this result contains readable scan state.
         *
         * @return {@code true} only for {@link Status#ACCEPTED}
         */
        public boolean accepted() {
            return status == Status.ACCEPTED;
        }
    }

    /**
     * Non-consuming read access to an existing preview.
     *
     * @param status access outcome
     * @param preview issued preview when accepted, or {@code null} for every rejection
     */
    public record PreviewAccess(Status status, @Nullable IssuedPreview preview) {
        /**
         * Enforces that accepted access has a preview and rejected access does not.
         *
         * @param status access outcome
         * @param preview issued preview for acceptance, otherwise {@code null}
         */
        public PreviewAccess {
            Objects.requireNonNull(status, "status");
            if ((status == Status.ACCEPTED) != (preview != null)) {
                throw new IllegalArgumentException("Only accepted preview access may contain preview state");
            }
        }

        /**
         * Creates accepted non-consuming preview access.
         *
         * @param preview non-null issued preview view
         * @return accepted access containing {@code preview}
         */
        public static PreviewAccess accepted(IssuedPreview preview) {
            return new PreviewAccess(Status.ACCEPTED, Objects.requireNonNull(preview, "preview"));
        }

        /**
         * Creates rejected preview access without exposing session state.
         *
         * @param status any status other than {@link Status#ACCEPTED}
         * @return rejected access with a {@code null} preview
         */
        public static PreviewAccess rejected(Status status) {
            if (status == Status.ACCEPTED) {
                throw new IllegalArgumentException("Accepted is not a rejection status");
            }
            return new PreviewAccess(status, null);
        }

        /**
         * Reports whether this result contains readable preview state.
         *
         * @return {@code true} only for {@link Status#ACCEPTED}
         */
        public boolean accepted() {
            return status == Status.ACCEPTED;
        }
    }

    /**
     * Result of issuing or replacing a server-retained preview.
     *
     * @param status issuance outcome
     * @param preview newly issued preview when accepted, or {@code null} for every rejection
     */
    public record PreviewIssue(Status status, @Nullable IssuedPreview preview) {
        /**
         * Enforces that accepted issuance has a preview and rejected issuance does not.
         *
         * @param status issuance outcome
         * @param preview issued preview for acceptance, otherwise {@code null}
         */
        public PreviewIssue {
            Objects.requireNonNull(status, "status");
            if ((status == Status.ACCEPTED) != (preview != null)) {
                throw new IllegalArgumentException(
                        "Accepted preview issues require a preview and rejections cannot include one");
            }
        }

        /**
         * Creates an accepted preview-issuance result.
         *
         * @param preview non-null newly issued preview
         * @return accepted result containing {@code preview}
         */
        public static PreviewIssue accepted(IssuedPreview preview) {
            return new PreviewIssue(Status.ACCEPTED, Objects.requireNonNull(preview, "preview"));
        }

        /**
         * Creates a rejected preview-issuance result.
         *
         * @param status any status other than {@link Status#ACCEPTED}
         * @return rejected result with a {@code null} preview
         */
        public static PreviewIssue rejected(Status status) {
            if (status == Status.ACCEPTED) {
                throw new IllegalArgumentException("Accepted is not a rejection status");
            }
            return new PreviewIssue(status, null);
        }

        /**
         * Reports whether a new preview and commit token were issued.
         *
         * @return {@code true} only for {@link Status#ACCEPTED}
         */
        public boolean accepted() {
            return status == Status.ACCEPTED;
        }
    }

    /**
     * One-use commit-token result carrying server-retained plan data only after all checks pass.
     *
     * @param status commit-token outcome
     * @param request bound preview request when accepted, or {@code null} when rejected
     * @param plan bound server-built plan when accepted, or {@code null} when rejected
     * @param targetProfileId normalized, bounded new profile ID when accepted, or {@code null} when rejected
     */
    public record CommitAttempt(
            Status status,
            @Nullable PreviewRequest request,
            OreImportModels.@Nullable Plan plan,
            @Nullable String targetProfileId) {
        /**
         * Enforces an all-or-none payload: accepted attempts contain every value and rejections contain none.
         *
         * @param status commit-token outcome
         * @param request accepted bound request, or {@code null}
         * @param plan accepted bound plan, or {@code null}
         * @param targetProfileId accepted normalized, bounded target profile ID, or {@code null}
         */
        public CommitAttempt {
            Objects.requireNonNull(status, "status");
            boolean hasCommit = request != null || plan != null || targetProfileId != null;
            if ((status == Status.ACCEPTED) != hasCommit
                    || (hasCommit && (request == null || plan == null || targetProfileId == null))) {
                throw new IllegalArgumentException("Only accepted commit attempts may contain commit state");
            }
        }

        /**
         * Creates an accepted commit authorization tuple.
         *
         * <p>The tuple does not itself save, overwrite, or activate a profile; callers must apply authorization and a
         * create-only persistence operation separately.
         *
         * @param request server-retained preview request
         * @param plan server-retained validated plan
         * @param targetProfileId normalized, bounded new profile ID
         * @return accepted attempt containing all commit inputs
         */
        public static CommitAttempt accepted(
                PreviewRequest request, OreImportModels.Plan plan, String targetProfileId) {
            return new CommitAttempt(
                    Status.ACCEPTED,
                    Objects.requireNonNull(request, "request"),
                    Objects.requireNonNull(plan, "plan"),
                    Objects.requireNonNull(targetProfileId, "targetProfileId"));
        }

        /**
         * Creates a rejected commit attempt with no plan or target data.
         *
         * @param status any status other than {@link Status#ACCEPTED}
         * @return rejected attempt whose nullable payload components are all {@code null}
         */
        public static CommitAttempt rejected(Status status) {
            if (status == Status.ACCEPTED) {
                throw new IllegalArgumentException("Accepted is not a rejection status");
            }
            return new CommitAttempt(status, null, null, null);
        }

        /**
         * Reports whether the one-use token produced an authorized commit tuple.
         *
         * @return {@code true} only for {@link Status#ACCEPTED}
         */
        public boolean accepted() {
            return status == Status.ACCEPTED;
        }
    }

    /** Ordered outcomes for scan admission, session access, preview issuance, and one-use commit attempts. */
    public enum Status {
        /** The requested transition or access passed every applicable check. */
        ACCEPTED,
        /** The owner has no active scan session. */
        NO_ACTIVE_SCAN,
        /** The owner has no active preview session. */
        NO_ACTIVE_PREVIEW,
        /** The supplied token is null, malformed, unknown, or belongs to a different owner. */
        INVALID_TOKEN,
        /** The admission or session reached its fixed UTC expiry timestamp. */
        EXPIRED,
        /** The active ore-profile revision differs from the captured revision. */
        REVISION_CHANGED,
        /** Installed block or tag membership differs from the captured registry fingerprint. */
        REGISTRY_CHANGED,
        /** The source profile differs from the captured canonical-content fingerprint. */
        BASE_CHANGED,
        /** Selected groups, base profile, or target profile ID do not match bounded server state. */
        INVALID_REQUEST,
        /** The server-built proposed profile failed final validation. */
        PLAN_INVALID,
        /** The owner requested another registry scan before the cooldown elapsed. */
        RATE_LIMITED
    }

    private static final class Token {
        private final String value;
        private final byte[] digest;

        private Token(String value, byte[] digest) {
            this.value = value;
            this.digest = digest;
        }

        private String value() {
            return value;
        }

        private byte[] digest() {
            return digest;
        }
    }

    private static final class ScanSession {
        private final byte[] tokenDigest;
        private final long expiresAtEpochMillis;
        private final SnapshotBinding binding;
        private final OreImportModels.DiscoveryResult discovery;

        private ScanSession(
                byte[] tokenDigest,
                long expiresAtEpochMillis,
                SnapshotBinding binding,
                OreImportModels.DiscoveryResult discovery) {
            this.tokenDigest = tokenDigest;
            this.expiresAtEpochMillis = expiresAtEpochMillis;
            this.binding = binding;
            this.discovery = discovery;
        }

        private byte[] tokenDigest() {
            return tokenDigest;
        }

        private long expiresAtEpochMillis() {
            return expiresAtEpochMillis;
        }

        private SnapshotBinding binding() {
            return binding;
        }

        private OreImportModels.DiscoveryResult discovery() {
            return discovery;
        }
    }

    private static final class PreviewSession {
        private final byte[] tokenDigest;
        private final long expiresAtEpochMillis;
        private final SnapshotBinding binding;
        private final PreviewRequest request;
        private final OreImportModels.Plan plan;

        private PreviewSession(
                byte[] tokenDigest,
                long expiresAtEpochMillis,
                SnapshotBinding binding,
                PreviewRequest request,
                OreImportModels.Plan plan) {
            this.tokenDigest = tokenDigest;
            this.expiresAtEpochMillis = expiresAtEpochMillis;
            this.binding = binding;
            this.request = request;
            this.plan = plan;
        }

        private byte[] tokenDigest() {
            return tokenDigest;
        }

        private long expiresAtEpochMillis() {
            return expiresAtEpochMillis;
        }

        private SnapshotBinding binding() {
            return binding;
        }

        private PreviewRequest request() {
            return request;
        }

        private OreImportModels.Plan plan() {
            return plan;
        }
    }
}
