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
import java.util.Set;

/** Pure, deterministic planner for previewing automatic backup pruning. */
public final class BackupRetentionPlanner {
    private static final int MINIMUM_NEWEST_BACKUPS = 2;
    private static final Comparator<Candidate> NEWEST_FIRST =
            Comparator.comparingLong(Candidate::createdAtEpochMillis).reversed().thenComparing(Candidate::id);
    private static final Comparator<Candidate> OLDEST_FIRST =
            Comparator.comparingLong(Candidate::createdAtEpochMillis).thenComparing(Candidate::id);

    private BackupRetentionPlanner() {}

    public static Plan plan(
            BackupRetentionSettings settings,
            List<Candidate> suppliedCandidates,
            Set<String> pendingBackupIds,
            Instant now) {
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
        Map<String, LinkedHashSet<ProtectionReason>> protectionReasons = protections(candidates, pending);
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
                .map(entry -> new Prune(
                        entry.getKey(),
                        byId.get(entry.getKey()).createdAtEpochMillis(),
                        byId.get(entry.getKey()).sizeBytes(),
                        entry.getValue()))
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

    private static Map<String, LinkedHashSet<ProtectionReason>> protections(
            List<Candidate> candidates, Set<String> pendingBackupIds) {
        Set<String> pending = pendingBackupIds == null ? Set.of() : pendingBackupIds;
        Map<String, LinkedHashSet<ProtectionReason>> result = new LinkedHashMap<>();
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

    private static void protect(
            Map<String, LinkedHashSet<ProtectionReason>> protections, String id, ProtectionReason reason) {
        protections.computeIfAbsent(id, ignored -> new LinkedHashSet<>()).add(reason);
    }

    private static Map<String, List<ProtectionReason>> immutableProtections(
            Map<String, LinkedHashSet<ProtectionReason>> supplied) {
        Map<String, List<ProtectionReason>> result = new LinkedHashMap<>();
        supplied.forEach((id, reasons) -> {
            if (!reasons.isEmpty()) {
                result.put(id, List.copyOf(reasons));
            }
        });
        return java.util.Collections.unmodifiableMap(result);
    }

    private static List<Candidate> normalize(List<Candidate> supplied) {
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

    private static long ageCutoff(Instant now, long days) {
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
            BackupRetentionSettings settings, List<Candidate> candidates, Set<String> kept, Instant now, long bytes) {
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

    public record Candidate(
            String id,
            long createdAtEpochMillis,
            long sizeBytes,
            boolean pinned,
            boolean valid,
            boolean manifestPresent,
            boolean verified,
            boolean restorable) {
        public Candidate {
            id = id == null ? "" : id;
        }

        /** Source-compatible constructor for callers supplying known-good candidates. */
        public Candidate(String id, long createdAtEpochMillis, long sizeBytes, boolean pinned) {
            this(id, createdAtEpochMillis, sizeBytes, pinned, true, true, true, true);
        }
    }

    public record Prune(String id, long createdAtEpochMillis, long sizeBytes, List<Reason> reasons) {
        public Prune {
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }
    }

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

    public enum Reason {
        AGE,
        COUNT,
        TOTAL_BYTES
    }

    public enum ProtectionReason {
        PINNED,
        PENDING_OPERATION,
        NEWEST_TWO,
        INVALID,
        MANIFEST_MISSING,
        VERIFICATION_NOT_CURRENT,
        NOT_RESTORABLE,
        SIZE_UNKNOWN,
        TIMESTAMP_UNKNOWN
    }
}
