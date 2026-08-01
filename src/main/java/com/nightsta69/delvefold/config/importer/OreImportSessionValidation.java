package com.nightsta69.delvefold.config.importer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Centralizes bounded input, state-binding, and fixed-expiry validation for ore-import sessions. */
final class OreImportSessionValidation {
    private static final int MAX_STATE_KEY_LENGTH = 256;
    private static final int MAX_PROFILE_ID_LENGTH = 128;

    private OreImportSessionValidation() {}

    static boolean validAdmission(@Nullable Long admittedAt, long nowEpochMillis, Duration lifetime) {
        return admittedAt != null
                && nowEpochMillis >= admittedAt
                && !expired(expiresAt(admittedAt, lifetime), nowEpochMillis);
    }

    static long expiresAt(long nowEpochMillis, Duration duration) {
        long ttl = duration.toMillis();
        return nowEpochMillis > Long.MAX_VALUE - ttl ? Long.MAX_VALUE : nowEpochMillis + ttl;
    }

    static boolean expired(long expiresAtEpochMillis, long nowEpochMillis) {
        return nowEpochMillis >= expiresAtEpochMillis;
    }

    static OreImportSessionService.Status compareBindings(
            OreImportSessionService.SnapshotBinding expected, OreImportSessionService.SnapshotBinding current) {
        if (expected.expectedOreRevision() != current.expectedOreRevision()) {
            return OreImportSessionService.Status.REVISION_CHANGED;
        }
        if (!expected.registryFingerprint().equals(current.registryFingerprint())) {
            return OreImportSessionService.Status.REGISTRY_CHANGED;
        }
        if (!expected.baseContentHash().equals(current.baseContentHash())) {
            return OreImportSessionService.Status.BASE_CHANGED;
        }
        return OreImportSessionService.Status.ACCEPTED;
    }

    static boolean validRequest(
            OreImportModels.DiscoveryResult discovery,
            OreImportSessionService.PreviewRequest request,
            OreImportModels.Plan plan) {
        if (!request.baseProfileId().equals(plan.baseProfileId())) {
            return false;
        }
        Set<String> available = new HashSet<>();
        for (OreImportModels.Group group : discovery.groups()) {
            available.add(group.id());
        }
        return available.containsAll(request.selectedGroupIds());
    }

    static String normalizedStateKey(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > MAX_STATE_KEY_LENGTH) {
            throw new IllegalArgumentException(name + " must contain 1-" + MAX_STATE_KEY_LENGTH + " characters");
        }
        return normalized;
    }

    static String normalizedBaseProfileId(@Nullable String value) {
        String normalized = normalizedProfileId(value);
        if (normalized == null) {
            throw new IllegalArgumentException(
                    "Base profile ID must contain 1-" + MAX_PROFILE_ID_LENGTH + " characters");
        }
        return normalized;
    }

    static @Nullable String normalizedProfileId(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > MAX_PROFILE_ID_LENGTH) {
            return null;
        }
        return normalized;
    }

    static List<String> normalizedSelectedGroupIds(List<String> selectedGroupIds) {
        Objects.requireNonNull(selectedGroupIds, "selectedGroupIds");
        if (selectedGroupIds.isEmpty() || selectedGroupIds.size() > OreImportModels.MAX_SELECTED_GROUPS) {
            throw new IllegalArgumentException(
                    "A preview must select 1-" + OreImportModels.MAX_SELECTED_GROUPS + " groups");
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
        return List.copyOf(normalizedGroups);
    }
}
