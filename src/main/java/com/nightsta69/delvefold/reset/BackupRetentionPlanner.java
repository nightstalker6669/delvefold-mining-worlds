package com.nightsta69.delvefold.reset;

import com.nightsta69.delvefold.config.model.BackupRetentionSettings;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Pure, deterministic planner for previewing automatic backup pruning. */
public final class BackupRetentionPlanner {
    private static final int MINIMUM_NEWEST_BACKUPS = 2;
    private static final Comparator<Candidate> NEWEST_FIRST =
            Comparator.comparingLong(Candidate::createdAtEpochMillis).reversed().thenComparing(Candidate::id);
    private static final Comparator<Candidate> OLDEST_FIRST =
            Comparator.comparingLong(Candidate::createdAtEpochMillis).thenComparing(Candidate::id);

    private BackupRetentionPlanner() {}

    /**
     * Computes a deterministic prune preview without reading or changing the filesystem.
     *
     * <p>Pinned, pending, invalid, unverified, non-restorable, and the newest two backups are never selected. Unknown
     * sizes contribute zero bytes but protect their candidate. Equal timestamps are ordered by identifier.
     *
     * @param settings retention limits, or {@code null} to produce a disabled plan
     * @param suppliedCandidates candidate snapshots; {@code null} entries and blank identifiers are ignored
     * @param pendingBackupIds backup identifiers referenced by accepted or persisted operations, or {@code null}
     * @param now age-cutoff reference instant, or {@code null} to use the epoch
     * @return immutable preview with oldest-first prunes, remaining totals, warnings, and protection reasons
     * @throws IllegalArgumentException if one identifier has conflicting candidate snapshots
     */
    public static Plan plan(
            @Nullable BackupRetentionSettings settings,
            @Nullable List<@Nullable Candidate> suppliedCandidates,
            @Nullable Set<String> pendingBackupIds,
            @Nullable Instant now) {
        List<Candidate> candidates = normalize(suppliedCandidates);
        long beforeBytes = totalBytes(candidates);
        if (settings == null || !settings.enabled()) {
            return new Plan(
                    false,
                    List.of(),
                    candidates.size(),
                    candidates.size(),
                    beforeBytes,
                    beforeBytes,
                    true,
                    List.of(),
                    immutableProtections(protections(candidates, pendingBackupIds)));
        }

        Set<String> pending = pendingBackupIds == null ? Set.of() : Set.copyOf(pendingBackupIds);
        Map<String, Set<ProtectionReason>> protectionReasons = protections(candidates, pending);
        candidates.stream()
                .sorted(NEWEST_FIRST)
                .limit(MINIMUM_NEWEST_BACKUPS)
                .forEach(candidate -> protect(protectionReasons, candidate.id(), ProtectionReason.NEWEST_TWO));
        Set<String> protectedIds = protectionReasons.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());

        List<Candidate> oldestFirst = candidates.stream().sorted(OLDEST_FIRST).toList();
        Set<String> kept = new HashSet<>();
        candidates.stream().map(Candidate::id).forEach(kept::add);
        Map<String, List<Reason>> removals = new LinkedHashMap<>();

        if (settings.maxAgeDays() > 0) {
            long cutoff = ageCutoff(now, settings.maxAgeDays());
            for (Candidate candidate : oldestFirst) {
                if (candidate.createdAtEpochMillis() < cutoff && !protectedIds.contains(candidate.id())) {
                    remove(candidate, Reason.AGE, kept, removals);
                }
            }
        }

        if (settings.maxCount() > 0) {
            for (Candidate candidate : oldestFirst) {
                if (kept.size() <= settings.maxCount()) {
                    break;
                }
                if (!protectedIds.contains(candidate.id())) {
                    remove(candidate, Reason.COUNT, kept, removals);
                }
            }
        }

        if (settings.maxTotalBytes() > 0) {
            long keptBytes = retainedBytes(candidates, kept);
            for (Candidate candidate : oldestFirst) {
                if (keptBytes <= settings.maxTotalBytes()) {
                    break;
                }
                if (kept.contains(candidate.id()) && !protectedIds.contains(candidate.id())) {
                    remove(candidate, Reason.TOTAL_BYTES, kept, removals);
                    keptBytes = retainedBytes(candidates, kept);
                }
            }
        }

        Map<String, Candidate> byId = new HashMap<>();
        candidates.forEach(candidate -> byId.put(candidate.id(), candidate));
        List<Prune> prunes = removals.entrySet().stream()
                .map(entry -> {
                    Candidate candidate = Objects.requireNonNull(
                            byId.get(entry.getKey()), "removal must reference a normalized backup candidate");
                    return new Prune(
                            entry.getKey(), candidate.createdAtEpochMillis(), candidate.sizeBytes(), entry.getValue());
                })
                .sorted(Comparator.comparingLong(Prune::createdAtEpochMillis).thenComparing(Prune::id))
                .toList();
        long afterBytes = retainedBytes(candidates, kept);
        List<String> unmet = unmetConstraints(settings, candidates, kept, now, afterBytes);
        return new Plan(
                true,
                prunes,
                candidates.size(),
                kept.size(),
                beforeBytes,
                afterBytes,
                unmet.isEmpty(),
                unmet,
                immutableProtections(protectionReasons));
    }

    /**
     * Converts a catalog summary into the immutable planner input without filesystem access.
     *
     * @param summary current catalog snapshot
     * @return planner candidate preserving all protection-relevant flags and measured bytes
     */
    public static Candidate fromSummary(WorldBackupCatalog.BackupSummary summary) {
        return new Candidate(
                summary.id(),
                summary.createdAtEpochMillis(),
                summary.sizeBytes(),
                summary.pinned(),
                summary.valid(),
                summary.manifestPresent(),
                summary.verified(),
                summary.restorable());
    }

    private static Map<String, Set<ProtectionReason>> protections(
            List<Candidate> candidates, @Nullable Set<String> pendingBackupIds) {
        Set<String> pending = pendingBackupIds == null ? Set.of() : pendingBackupIds;
        Map<String, Set<ProtectionReason>> result = new LinkedHashMap<>();
        for (Candidate candidate : candidates) {
            result.put(candidate.id(), new LinkedHashSet<>());
            if (candidate.pinned()) {
                protect(result, candidate.id(), ProtectionReason.PINNED);
            }
            if (pending.contains(candidate.id())) {
                protect(result, candidate.id(), ProtectionReason.PENDING_OPERATION);
            }
            if (!candidate.valid()) {
                protect(result, candidate.id(), ProtectionReason.INVALID);
            }
            if (!candidate.manifestPresent()) {
                protect(result, candidate.id(), ProtectionReason.MANIFEST_MISSING);
            }
            if (!candidate.verified()) {
                protect(result, candidate.id(), ProtectionReason.VERIFICATION_NOT_CURRENT);
            }
            if (!candidate.restorable()) {
                protect(result, candidate.id(), ProtectionReason.NOT_RESTORABLE);
            }
            if (candidate.sizeBytes() < 0L) {
                protect(result, candidate.id(), ProtectionReason.SIZE_UNKNOWN);
            }
            if (candidate.createdAtEpochMillis() <= 0L) {
                protect(result, candidate.id(), ProtectionReason.TIMESTAMP_UNKNOWN);
            }
        }
        return result;
    }

    private static void protect(Map<String, Set<ProtectionReason>> protections, String id, ProtectionReason reason) {
        protections.computeIfAbsent(id, ignored -> new LinkedHashSet<>()).add(reason);
    }

    private static Map<String, List<ProtectionReason>> immutableProtections(
            Map<String, Set<ProtectionReason>> supplied) {
        Map<String, List<ProtectionReason>> result = new LinkedHashMap<>();
        supplied.forEach((id, reasons) -> {
            if (!reasons.isEmpty()) {
                result.put(id, List.copyOf(reasons));
            }
        });
        return java.util.Collections.unmodifiableMap(result);
    }

    private static List<Candidate> normalize(@Nullable List<@Nullable Candidate> supplied) {
        if (supplied == null || supplied.isEmpty()) {
            return List.of();
        }
        Map<String, Candidate> unique = new HashMap<>();
        for (Candidate candidate : supplied) {
            if (candidate == null || candidate.id().isBlank()) {
                continue;
            }
            Candidate previous = unique.putIfAbsent(candidate.id(), candidate);
            if (previous != null && !previous.equals(candidate)) {
                throw new IllegalArgumentException("Conflicting backup retention candidate: " + candidate.id());
            }
        }
        return unique.values().stream().sorted(NEWEST_FIRST).toList();
    }

    private static void remove(
            Candidate candidate, Reason reason, Set<String> kept, Map<String, List<Reason>> removals) {
        if (!kept.remove(candidate.id())) {
            return;
        }
        removals.computeIfAbsent(candidate.id(), ignored -> new ArrayList<>()).add(reason);
    }

    private static long ageCutoff(@Nullable Instant now, long days) {
        Instant reference = now == null ? Instant.EPOCH : now;
        try {
            return reference.minus(Duration.ofDays(days)).toEpochMilli();
        } catch (ArithmeticException exception) {
            return Long.MIN_VALUE;
        }
    }

    private static long totalBytes(List<Candidate> candidates) {
        long result = 0L;
        for (Candidate candidate : candidates) {
            result = saturatingAdd(result, normalizedSize(candidate.sizeBytes()));
        }
        return result;
    }

    private static long retainedBytes(List<Candidate> candidates, Set<String> kept) {
        long result = 0L;
        for (Candidate candidate : candidates) {
            if (kept.contains(candidate.id())) {
                result = saturatingAdd(result, normalizedSize(candidate.sizeBytes()));
            }
        }
        return result;
    }

    private static long normalizedSize(long size) {
        return Math.max(0L, size);
    }

    private static long saturatingAdd(long left, long right) {
        if (Long.MAX_VALUE - left < right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static List<String> unmetConstraints(
            BackupRetentionSettings settings,
            List<Candidate> candidates,
            Set<String> kept,
            @Nullable Instant now,
            long bytes) {
        List<String> result = new ArrayList<>();
        if (settings.maxAgeDays() > 0) {
            long cutoff = ageCutoff(now, settings.maxAgeDays());
            boolean hasProtectedExpired = candidates.stream()
                    .anyMatch(candidate -> kept.contains(candidate.id()) && candidate.createdAtEpochMillis() < cutoff);
            if (hasProtectedExpired) {
                result.add("max_age_days cannot remove one or more protected backups");
            }
        }
        if (settings.maxCount() > 0 && kept.size() > settings.maxCount()) {
            result.add("max_count is below the number of protected backups");
        }
        if (settings.maxTotalBytes() > 0 && bytes > settings.maxTotalBytes()) {
            result.add("max_total_bytes is below the size of protected backups");
        }
        return List.copyOf(result);
    }

    /**
     * One normalized backup snapshot considered by the pure retention planner.
     *
     * @param id unique normalized backup identifier
     * @param createdAtEpochMillis creation time in epoch milliseconds; nonpositive values are protected as unknown
     * @param sizeBytes measured recursive size in bytes; negative values are protected as unknown
     * @param pinned whether an administrator pinned the backup
     * @param valid whether catalog safety and metadata checks passed
     * @param manifestPresent whether a manifest exists
     * @param verified whether the current manifest has a valid verification receipt
     * @param restorable whether lifecycle policy currently permits restoration
     */
    public record Candidate(
            String id,
            long createdAtEpochMillis,
            long sizeBytes,
            boolean pinned,
            boolean valid,
            boolean manifestPresent,
            boolean verified,
            boolean restorable) {
        /**
         * Normalizes a nullable persisted identifier to an empty, subsequently ignored identifier.
         *
         * @param id unique normalized backup identifier
         * @param createdAtEpochMillis creation time in epoch milliseconds
         * @param sizeBytes measured recursive size in bytes
         * @param pinned whether an administrator pinned the backup
         * @param valid whether catalog validation passed
         * @param manifestPresent whether a manifest exists
         * @param verified whether verification is current
         * @param restorable whether restoration is currently permitted
         */
        public Candidate {
            id = id == null ? "" : id;
        }

        /**
         * Creates a source-compatible known-good candidate used by pre-1.3 callers and tests.
         *
         * @param id unique normalized backup identifier
         * @param createdAtEpochMillis creation time in epoch milliseconds
         * @param sizeBytes measured recursive size in bytes
         * @param pinned whether an administrator pinned the backup
         */
        public Candidate(String id, long createdAtEpochMillis, long sizeBytes, boolean pinned) {
            this(id, createdAtEpochMillis, sizeBytes, pinned, true, true, true, true);
        }
    }

    /**
     * One backup selected for deletion by one or more configured constraints.
     *
     * @param id normalized backup identifier
     * @param createdAtEpochMillis creation time in epoch milliseconds
     * @param sizeBytes measured recursive size in bytes
     * @param reasons immutable ordered constraints requiring removal
     */
    public record Prune(String id, long createdAtEpochMillis, long sizeBytes, List<Reason> reasons) {
        /**
         * Defensively snapshots prune reasons.
         *
         * @param id normalized backup identifier
         * @param createdAtEpochMillis creation time in epoch milliseconds
         * @param sizeBytes measured recursive size in bytes
         * @param reasons constraints requiring removal
         */
        public Prune {
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }
    }

    /**
     * Complete immutable retention preview consumed by diagnostics, audit, and the pruning service.
     *
     * @param enabled whether automatic retention is enabled
     * @param prunes immutable oldest-first proposed deletions
     * @param beforeCount candidate count before pruning
     * @param afterCount retained count after proposed pruning
     * @param beforeBytes normalized total bytes before pruning
     * @param afterBytes normalized retained bytes after proposed pruning
     * @param constraintsSatisfied whether protected backups still fit every configured limit
     * @param warnings immutable explanations for unsatisfied limits
     * @param protections immutable nonempty protection-reason lists keyed by backup identifier
     */
    public record Plan(
            boolean enabled,
            List<Prune> prunes,
            int beforeCount,
            int afterCount,
            long beforeBytes,
            long afterBytes,
            boolean constraintsSatisfied,
            List<String> warnings,
            Map<String, List<ProtectionReason>> protections) {
        /**
         * Defensively snapshots every collection and nested protection list.
         *
         * @param enabled whether automatic retention is enabled
         * @param prunes proposed deletions
         * @param beforeCount candidate count before pruning
         * @param afterCount retained count after proposed pruning
         * @param beforeBytes normalized total bytes before pruning
         * @param afterBytes normalized retained bytes after pruning
         * @param constraintsSatisfied whether all limits can be met
         * @param warnings unsatisfied-limit explanations
         * @param protections protection reasons keyed by backup identifier
         */
        public Plan {
            prunes = prunes == null ? List.of() : List.copyOf(prunes);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
            if (protections == null || protections.isEmpty()) {
                protections = Map.of();
            } else {
                Map<String, List<ProtectionReason>> copy = new LinkedHashMap<>();
                protections.forEach((id, reasons) -> copy.put(id, reasons == null ? List.of() : List.copyOf(reasons)));
                protections = java.util.Collections.unmodifiableMap(copy);
            }
        }
    }

    /** Retention constraint that selected an unprotected backup for pruning. */
    public enum Reason {
        /** Backup creation time is older than the configured maximum age. */
        AGE,

        /** Retained backup count exceeds the configured maximum. */
        COUNT,

        /** Retained measured bytes exceed the configured maximum. */
        TOTAL_BYTES
    }

    /** Reason a candidate must remain regardless of configured retention limits. */
    public enum ProtectionReason {
        /** Administrator explicitly pinned the backup. */
        PINNED,

        /** An accepted or persisted lifecycle operation references the backup. */
        PENDING_OPERATION,

        /** The backup is one of the two newest normalized candidates. */
        NEWEST_TWO,

        /** Catalog validation did not establish a safe backup. */
        INVALID,

        /** The backup has no integrity manifest. */
        MANIFEST_MISSING,

        /** The manifest has no current successful verification receipt. */
        VERIFICATION_NOT_CURRENT,

        /** Lifecycle policy does not currently permit restoration. */
        NOT_RESTORABLE,

        /** Recursive byte measurement was unavailable. */
        SIZE_UNKNOWN,

        /** Creation time was absent or invalid. */
        TIMESTAMP_UNKNOWN
    }
}
