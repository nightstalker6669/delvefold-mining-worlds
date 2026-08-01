package com.nightsta69.delvefold.config.importer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Owns the short-lived, server-authoritative state for the guided ore importer.
 *
 * <p>Only opaque random tokens leave this service. Discovery results and plans
 * stay server-side and are bound to their owner and the exact configuration and
 * registry state from which they were produced.</p>
 */
public final class OreImportSessionService {
    public static final Duration SESSION_TTL = Duration.ofMinutes(5);
    public static final Duration SCAN_COOLDOWN = Duration.ofSeconds(2);
    private static final int TOKEN_BYTES = 32;
    private static final int TOKEN_LENGTH = 43;
    private static final int MAX_STATE_KEY_LENGTH = 256;
    private static final int MAX_PROFILE_ID_LENGTH = 128;
    private static final OreImportSessionService INSTANCE =
            new OreImportSessionService(Clock.systemUTC(), secureTokenSource());

    private final Object lock = new Object();
    private final Clock clock;
    private final TokenSource tokens;
    private final Map<UUID, ScanSession> scans = new HashMap<>();
    private final Map<UUID, PreviewSession> previews = new HashMap<>();
    private final Map<UUID, Long> lastScanRequests = new HashMap<>();
    private final Map<UUID, Long> scanAdmissions = new HashMap<>();

    public OreImportSessionService() {
        this(Clock.systemUTC(), secureTokenSource());
    }

    OreImportSessionService(Clock clock, TokenSource tokens) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.tokens = Objects.requireNonNull(tokens, "tokens");
    }

    public static OreImportSessionService get() {
        return INSTANCE;
    }

    /**
     * Atomically rate-limits and reserves one registry scan for a player.
     * Callers must receive an accepted result before doing discovery and then
     * complete the reservation through {@link #issueScan(UUID, SnapshotBinding, OreImportModels.DiscoveryResult)}.
     */
    public ScanAdmission mayIssueScan(UUID owner) {
        Objects.requireNonNull(owner, "owner");
        synchronized (lock) {
            long now = clock.millis();
            Long previous = lastScanRequests.get(owner);
            long retryAt = previous == null ? now : expiresAt(previous, SCAN_COOLDOWN);
            if (previous != null && now < retryAt) {
                return ScanAdmission.rejected(retryAt);
            }
            lastScanRequests.put(owner, now);
            scanAdmissions.put(owner, now);
            return ScanAdmission.accepted(now);
        }
    }

    /** Starts a fresh scan and invalidates every older import token owned by the player. */
    public IssuedScan issueScan(
            UUID owner,
            SnapshotBinding binding,
            OreImportModels.DiscoveryResult discovery
    ) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(discovery, "discovery");
        synchronized (lock) {
            long now = clock.millis();
            Long admittedAt = scanAdmissions.remove(owner);
            if (admittedAt == null || now < admittedAt
                    || expired(expiresAt(admittedAt, SESSION_TTL), now)) {
                throw new IllegalStateException("Ore-import discovery was not admitted or its admission expired");
            }
            long expiresAt = expiresAt(now, SESSION_TTL);
            Token token = issueUniqueToken();
            scans.put(owner, new ScanSession(token.digest(), expiresAt, binding, discovery));
            previews.remove(owner);
            return new IssuedScan(token.value(), expiresAt, discovery);
        }
    }

    /** Reads an admitted scan for paging without consuming its token or extending its expiry. */
    public ScanAccess accessScan(UUID owner, String scanToken, SnapshotBinding current) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(current, "current");
        synchronized (lock) {
            ScanSession scan = scans.get(owner);
            if (scan == null) {
                return ScanAccess.rejected(Status.NO_ACTIVE_SCAN);
            }
            long now = clock.millis();
            if (expired(scan.expiresAtEpochMillis(), now)) {
                scans.remove(owner);
                return ScanAccess.rejected(Status.EXPIRED);
            }
            if (!tokenMatches(scan.tokenDigest(), scanToken)) {
                return ScanAccess.rejected(Status.INVALID_TOKEN);
            }
            Status state = compare(scan.binding(), current);
            if (state != Status.ACCEPTED) {
                scans.remove(owner);
                previews.remove(owner);
                return ScanAccess.rejected(state);
            }
            return ScanAccess.accepted(new IssuedScan(
                    scanToken, scan.expiresAtEpochMillis(), scan.discovery()));
        }
    }

    /**
     * Creates or replaces a preview after verifying its scan token and current server state.
     * Invalid previews may still be viewed, but their commit token is rejected as {@link Status#PLAN_INVALID}.
     */
    public PreviewIssue issuePreview(
            UUID owner,
            String scanToken,
            SnapshotBinding current,
            PreviewRequest request,
            OreImportModels.Plan plan
    ) {
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
            if (expired(scan.expiresAtEpochMillis(), now)) {
                scans.remove(owner);
                return PreviewIssue.rejected(Status.EXPIRED);
            }
            if (!tokenMatches(scan.tokenDigest(), scanToken)) {
                return PreviewIssue.rejected(Status.INVALID_TOKEN);
            }
            Status state = compare(scan.binding(), current);
            if (state != Status.ACCEPTED) {
                scans.remove(owner);
                previews.remove(owner);
                return PreviewIssue.rejected(state);
            }
            if (!validRequest(scan.discovery(), request, plan)) {
                return PreviewIssue.rejected(Status.INVALID_REQUEST);
            }

            Token commitToken = issueUniqueToken();
            long expiresAt = expiresAt(now, SESSION_TTL);
            PreviewSession preview = new PreviewSession(
                    commitToken.digest(), expiresAt, scan.binding(), request, plan);
            previews.put(owner, preview);
            return PreviewIssue.accepted(new IssuedPreview(
                    commitToken.value(), expiresAt, request, plan));
        }
    }

    /**
     * Consumes a matching commit token before checking mutable server state.
     * Consequently stale, invalid, and failed commit attempts cannot replay the token.
     */
    public CommitAttempt consumeCommit(
            UUID owner,
            String commitToken,
            SnapshotBinding current,
            String targetProfileId
    ) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(current, "current");
        synchronized (lock) {
            PreviewSession preview = previews.get(owner);
            if (preview == null) {
                return CommitAttempt.rejected(Status.NO_ACTIVE_PREVIEW);
            }
            long now = clock.millis();
            if (expired(preview.expiresAtEpochMillis(), now)) {
                previews.remove(owner);
                scans.remove(owner);
                return CommitAttempt.rejected(Status.EXPIRED);
            }
            if (!tokenMatches(preview.tokenDigest(), commitToken)) {
                return CommitAttempt.rejected(Status.INVALID_TOKEN);
            }

            // A recognized commit token is one-use even when a later check rejects the operation.
            previews.remove(owner);
            scans.remove(owner);

            Status state = compare(preview.binding(), current);
            if (state != Status.ACCEPTED) {
                return CommitAttempt.rejected(state);
            }
            String target = normalizedProfileId(targetProfileId);
            if (target == null) {
                return CommitAttempt.rejected(Status.INVALID_REQUEST);
            }
            if (!preview.plan().valid()) {
                return CommitAttempt.rejected(Status.PLAN_INVALID);
            }
            return CommitAttempt.accepted(preview.request(), preview.plan(), target);
        }
    }

    /** Reads a preview for paging without consuming its one-use commit token or extending its expiry. */
    public PreviewAccess accessPreview(UUID owner, String commitToken, SnapshotBinding current) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(current, "current");
        synchronized (lock) {
            PreviewSession preview = previews.get(owner);
            if (preview == null) {
                return PreviewAccess.rejected(Status.NO_ACTIVE_PREVIEW);
            }
            long now = clock.millis();
            if (expired(preview.expiresAtEpochMillis(), now)) {
                previews.remove(owner);
                scans.remove(owner);
                return PreviewAccess.rejected(Status.EXPIRED);
            }
            if (!tokenMatches(preview.tokenDigest(), commitToken)) {
                return PreviewAccess.rejected(Status.INVALID_TOKEN);
            }
            Status state = compare(preview.binding(), current);
            if (state != Status.ACCEPTED) {
                previews.remove(owner);
                scans.remove(owner);
                return PreviewAccess.rejected(state);
            }
            return PreviewAccess.accepted(new IssuedPreview(
                    commitToken, preview.expiresAtEpochMillis(), preview.request(), preview.plan()));
        }
    }

    public void invalidatePlayer(UUID owner) {
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

    public void invalidateAll() {
        synchronized (lock) {
            scans.clear();
            previews.clear();
            lastScanRequests.clear();
            scanAdmissions.clear();
        }
    }

    private Token issueUniqueToken() {
        for (int attempt = 0; attempt < 16; attempt++) {
            String value = requireToken(tokens.nextToken());
            byte[] digest = digest(value);
            if (!tokenInUse(digest)) {
                return new Token(value, digest);
            }
        }
        throw new IllegalStateException("Could not create a unique ore-import session token");
    }

    private boolean tokenInUse(byte[] candidate) {
        for (ScanSession session : scans.values()) {
            if (MessageDigest.isEqual(session.tokenDigest(), candidate)) {
                return true;
            }
        }
        for (PreviewSession session : previews.values()) {
            if (MessageDigest.isEqual(session.tokenDigest(), candidate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean validRequest(
            OreImportModels.DiscoveryResult discovery,
            PreviewRequest request,
            OreImportModels.Plan plan
    ) {
        if (!request.baseProfileId().equals(plan.baseProfileId())) {
            return false;
        }
        Set<String> available = new HashSet<>();
        for (OreImportModels.Group group : discovery.groups()) {
            available.add(group.id());
        }
        return available.containsAll(request.selectedGroupIds());
    }

    private static Status compare(SnapshotBinding expected, SnapshotBinding current) {
        if (expected.expectedOreRevision() != current.expectedOreRevision()) {
            return Status.REVISION_CHANGED;
        }
        if (!expected.registryFingerprint().equals(current.registryFingerprint())) {
            return Status.REGISTRY_CHANGED;
        }
        if (!expected.baseContentHash().equals(current.baseContentHash())) {
            return Status.BASE_CHANGED;
        }
        return Status.ACCEPTED;
    }

    private static boolean tokenMatches(byte[] expectedDigest, String supplied) {
        if (!validToken(supplied)) {
            return false;
        }
        return MessageDigest.isEqual(expectedDigest, digest(supplied));
    }

    private static String requireToken(String token) {
        if (!validToken(token)) {
            throw new IllegalStateException("Ore-import token source returned an invalid token");
        }
        return token;
    }

    private static boolean validToken(String token) {
        if (token == null || token.length() != TOKEN_LENGTH) {
            return false;
        }
        for (int index = 0; index < token.length(); index++) {
            char character = token.charAt(index);
            if (!(character >= 'a' && character <= 'z')
                    && !(character >= 'A' && character <= 'Z')
                    && !(character >= '0' && character <= '9')
                    && character != '-' && character != '_') {
                return false;
            }
        }
        return true;
    }

    private static byte[] digest(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static TokenSource secureTokenSource() {
        SecureRandom random = new SecureRandom();
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return () -> {
            byte[] bytes = new byte[TOKEN_BYTES];
            random.nextBytes(bytes);
            return encoder.encodeToString(bytes);
        };
    }

    private static long expiresAt(long nowEpochMillis, Duration duration) {
        long ttl = duration.toMillis();
        return nowEpochMillis > Long.MAX_VALUE - ttl ? Long.MAX_VALUE : nowEpochMillis + ttl;
    }

    private static boolean expired(long expiresAtEpochMillis, long nowEpochMillis) {
        return nowEpochMillis >= expiresAtEpochMillis;
    }

    private static String normalizedStateKey(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > MAX_STATE_KEY_LENGTH) {
            throw new IllegalArgumentException(name + " must contain 1-" + MAX_STATE_KEY_LENGTH + " characters");
        }
        return normalized;
    }

    private static String normalizedProfileId(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > MAX_PROFILE_ID_LENGTH) {
            return null;
        }
        return normalized;
    }

    @FunctionalInterface
    interface TokenSource {
        String nextToken();
    }

    public record SnapshotBinding(
            long expectedOreRevision,
            String registryFingerprint,
            String baseContentHash
    ) {
        public SnapshotBinding {
            if (expectedOreRevision < 0L) {
                throw new IllegalArgumentException("Expected ore revision cannot be negative");
            }
            registryFingerprint = normalizedStateKey(registryFingerprint, "registryFingerprint");
            baseContentHash = normalizedStateKey(baseContentHash, "baseContentHash");
        }
    }

    public record PreviewRequest(String baseProfileId, List<String> selectedGroupIds) {
        public PreviewRequest {
            String normalizedProfile = normalizedProfileId(baseProfileId);
            if (normalizedProfile == null) {
                throw new IllegalArgumentException("Base profile ID must contain 1-" + MAX_PROFILE_ID_LENGTH
                        + " characters");
            }
            baseProfileId = normalizedProfile;
            Objects.requireNonNull(selectedGroupIds, "selectedGroupIds");
            if (selectedGroupIds.isEmpty()
                    || selectedGroupIds.size() > OreImportModels.MAX_SELECTED_GROUPS) {
                throw new IllegalArgumentException("A preview must select 1-"
                        + OreImportModels.MAX_SELECTED_GROUPS + " groups");
            }
            List<String> normalizedGroups = new ArrayList<>(selectedGroupIds.size());
            Set<String> unique = new HashSet<>();
            for (String groupId : selectedGroupIds) {
                String normalized = normalizedStateKey(groupId, "selectedGroupId");
                if (!unique.add(normalized)) {
                    throw new IllegalArgumentException("Selected group IDs must be unique");
                }
                normalizedGroups.add(normalized);
            }
            selectedGroupIds = List.copyOf(normalizedGroups);
        }
    }

    public record IssuedScan(
            String scanToken,
            long expiresAtEpochMillis,
            OreImportModels.DiscoveryResult discovery
    ) {
    }

    public record IssuedPreview(
            String commitToken,
            long expiresAtEpochMillis,
            PreviewRequest request,
            OreImportModels.Plan plan
    ) {
    }

    public record ScanAdmission(Status status, long admittedAtEpochMillis, long retryAtEpochMillis) {
        public ScanAdmission {
            Objects.requireNonNull(status, "status");
            if (status != Status.ACCEPTED && status != Status.RATE_LIMITED) {
                throw new IllegalArgumentException("Scan admission requires an accepted or rate-limited status");
            }
        }

        public static ScanAdmission accepted(long admittedAtEpochMillis) {
            return new ScanAdmission(Status.ACCEPTED, admittedAtEpochMillis, admittedAtEpochMillis);
        }

        public static ScanAdmission rejected(long retryAtEpochMillis) {
            return new ScanAdmission(Status.RATE_LIMITED, 0L, retryAtEpochMillis);
        }

        public boolean accepted() {
            return status == Status.ACCEPTED;
        }
    }

    public record ScanAccess(Status status, IssuedScan scan) {
        public ScanAccess {
            Objects.requireNonNull(status, "status");
            if ((status == Status.ACCEPTED) != (scan != null)) {
                throw new IllegalArgumentException("Only accepted scan access may contain scan state");
            }
        }

        public static ScanAccess accepted(IssuedScan scan) {
            return new ScanAccess(Status.ACCEPTED, Objects.requireNonNull(scan, "scan"));
        }

        public static ScanAccess rejected(Status status) {
            if (status == Status.ACCEPTED) {
                throw new IllegalArgumentException("Accepted is not a rejection status");
            }
            return new ScanAccess(status, null);
        }

        public boolean accepted() {
            return status == Status.ACCEPTED;
        }
    }

    public record PreviewAccess(Status status, IssuedPreview preview) {
        public PreviewAccess {
            Objects.requireNonNull(status, "status");
            if ((status == Status.ACCEPTED) != (preview != null)) {
                throw new IllegalArgumentException("Only accepted preview access may contain preview state");
            }
        }

        public static PreviewAccess accepted(IssuedPreview preview) {
            return new PreviewAccess(Status.ACCEPTED, Objects.requireNonNull(preview, "preview"));
        }

        public static PreviewAccess rejected(Status status) {
            if (status == Status.ACCEPTED) {
                throw new IllegalArgumentException("Accepted is not a rejection status");
            }
            return new PreviewAccess(status, null);
        }

        public boolean accepted() {
            return status == Status.ACCEPTED;
        }
    }

    public record PreviewIssue(Status status, IssuedPreview preview) {
        public PreviewIssue {
            Objects.requireNonNull(status, "status");
            if ((status == Status.ACCEPTED) != (preview != null)) {
                throw new IllegalArgumentException("Accepted preview issues require a preview and rejections cannot include one");
            }
        }

        public static PreviewIssue accepted(IssuedPreview preview) {
            return new PreviewIssue(Status.ACCEPTED, Objects.requireNonNull(preview, "preview"));
        }

        public static PreviewIssue rejected(Status status) {
            if (status == Status.ACCEPTED) {
                throw new IllegalArgumentException("Accepted is not a rejection status");
            }
            return new PreviewIssue(status, null);
        }

        public boolean accepted() {
            return status == Status.ACCEPTED;
        }
    }

    public record CommitAttempt(
            Status status,
            PreviewRequest request,
            OreImportModels.Plan plan,
            String targetProfileId
    ) {
        public CommitAttempt {
            Objects.requireNonNull(status, "status");
            boolean hasCommit = request != null || plan != null || targetProfileId != null;
            if ((status == Status.ACCEPTED) != hasCommit
                    || hasCommit && (request == null || plan == null || targetProfileId == null)) {
                throw new IllegalArgumentException("Only accepted commit attempts may contain commit state");
            }
        }

        public static CommitAttempt accepted(
                PreviewRequest request, OreImportModels.Plan plan, String targetProfileId) {
            return new CommitAttempt(Status.ACCEPTED, Objects.requireNonNull(request, "request"),
                    Objects.requireNonNull(plan, "plan"), Objects.requireNonNull(targetProfileId, "targetProfileId"));
        }

        public static CommitAttempt rejected(Status status) {
            if (status == Status.ACCEPTED) {
                throw new IllegalArgumentException("Accepted is not a rejection status");
            }
            return new CommitAttempt(status, null, null, null);
        }

        public boolean accepted() {
            return status == Status.ACCEPTED;
        }
    }

    public enum Status {
        ACCEPTED,
        NO_ACTIVE_SCAN,
        NO_ACTIVE_PREVIEW,
        INVALID_TOKEN,
        EXPIRED,
        REVISION_CHANGED,
        REGISTRY_CHANGED,
        BASE_CHANGED,
        INVALID_REQUEST,
        PLAN_INVALID,
        RATE_LIMITED
    }

    private record Token(String value, byte[] digest) {
    }

    private record ScanSession(
            byte[] tokenDigest,
            long expiresAtEpochMillis,
            SnapshotBinding binding,
            OreImportModels.DiscoveryResult discovery
    ) {
    }

    private record PreviewSession(
            byte[] tokenDigest,
            long expiresAtEpochMillis,
            SnapshotBinding binding,
            PreviewRequest request,
            OreImportModels.Plan plan
    ) {
    }
}
