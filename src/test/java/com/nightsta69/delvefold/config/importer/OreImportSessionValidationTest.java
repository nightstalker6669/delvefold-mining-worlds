package com.nightsta69.delvefold.config.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.OrePresets;
import com.nightsta69.delvefold.config.importer.OreImportModels.Candidate;
import com.nightsta69.delvefold.config.importer.OreImportModels.DiscoveryResult;
import com.nightsta69.delvefold.config.importer.OreImportModels.Evidence;
import com.nightsta69.delvefold.config.importer.OreImportModels.Group;
import com.nightsta69.delvefold.config.importer.OreImportModels.HostKind;
import com.nightsta69.delvefold.config.importer.OreImportModels.Plan;
import com.nightsta69.delvefold.config.importer.OreImportModels.Workload;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.validation.ValidationReport;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class OreImportSessionValidationTest {
    private static final OreImportSessionService.SnapshotBinding BINDING =
            new OreImportSessionService.SnapshotBinding(7L, "registry-a", "profile-a");

    @Test
    void fixedExpiryUsesAnInclusiveBoundaryAndSaturatingArithmetic() {
        Duration lifetime = Duration.ofMillis(200L);

        assertEquals(1_200L, OreImportSessionValidation.expiresAt(1_000L, lifetime));
        assertEquals(Long.MAX_VALUE, OreImportSessionValidation.expiresAt(Long.MAX_VALUE - 100L, lifetime));
        assertFalse(OreImportSessionValidation.expired(1_200L, 1_199L));
        assertTrue(OreImportSessionValidation.expired(1_200L, 1_200L));
        assertTrue(OreImportSessionValidation.expired(1_200L, 1_201L));
    }

    @Test
    void admissionRejectsMissingFutureAndBoundaryExpiredTimestamps() {
        Duration lifetime = Duration.ofMillis(200L);

        assertFalse(OreImportSessionValidation.validAdmission(null, 1_000L, lifetime));
        assertFalse(OreImportSessionValidation.validAdmission(1_001L, 1_000L, lifetime));
        assertTrue(OreImportSessionValidation.validAdmission(1_000L, 1_199L, lifetime));
        assertFalse(OreImportSessionValidation.validAdmission(1_000L, 1_200L, lifetime));
    }

    @Test
    void bindingComparisonRetainsRevisionRegistryThenProfilePrecedence() {
        assertEquals(
                OreImportSessionService.Status.REVISION_CHANGED,
                OreImportSessionValidation.compareBindings(
                        BINDING, new OreImportSessionService.SnapshotBinding(8L, "registry-b", "profile-b")));
        assertEquals(
                OreImportSessionService.Status.REGISTRY_CHANGED,
                OreImportSessionValidation.compareBindings(
                        BINDING, new OreImportSessionService.SnapshotBinding(7L, "registry-b", "profile-b")));
        assertEquals(
                OreImportSessionService.Status.BASE_CHANGED,
                OreImportSessionValidation.compareBindings(
                        BINDING, new OreImportSessionService.SnapshotBinding(7L, "registry-a", "profile-b")));
        assertEquals(
                OreImportSessionService.Status.ACCEPTED, OreImportSessionValidation.compareBindings(BINDING, BINDING));
    }

    @Test
    void stateKeysAreTrimmedAndBoundedWithoutChangingTheirCase() {
        assertEquals("Registry-Key", OreImportSessionValidation.normalizedStateKey("  Registry-Key  ", "state"));
        assertEquals("x".repeat(256), OreImportSessionValidation.normalizedStateKey("x".repeat(256), "state"));

        var blank = assertThrows(
                IllegalArgumentException.class, () -> OreImportSessionValidation.normalizedStateKey("  ", "state"));
        var oversized = assertThrows(
                IllegalArgumentException.class,
                () -> OreImportSessionValidation.normalizedStateKey("x".repeat(257), "state"));
        assertEquals("state must contain 1-256 characters", blank.getMessage());
        assertEquals("state must contain 1-256 characters", oversized.getMessage());
    }

    @Test
    void targetProfileIdsNormalizeOrRejectWithoutThrowing() {
        assertEquals("Imported_Tin", OreImportSessionValidation.normalizedProfileId("  Imported_Tin  "));
        assertEquals("x".repeat(128), OreImportSessionValidation.normalizedProfileId("x".repeat(128)));
        assertNull(OreImportSessionValidation.normalizedProfileId(null));
        assertNull(OreImportSessionValidation.normalizedProfileId("  "));
        assertNull(OreImportSessionValidation.normalizedProfileId("x".repeat(129)));
    }

    @Test
    void previewRequestNormalizationKeepsOrderAndRejectsInvalidOrDuplicateGroups() {
        List<String> normalized =
                OreImportSessionValidation.normalizedSelectedGroupIds(List.of(" example:tin ", "example:lead"));

        assertEquals(List.of("example:tin", "example:lead"), normalized);
        assertThrows(UnsupportedOperationException.class, () -> normalized.set(0, "changed"));
        assertThrows(
                IllegalArgumentException.class, () -> OreImportSessionValidation.normalizedSelectedGroupIds(List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> OreImportSessionValidation.normalizedSelectedGroupIds(List.of("example:tin", " example:tin ")));
        assertThrows(
                IllegalArgumentException.class,
                () -> OreImportSessionValidation.normalizedSelectedGroupIds(List.of(" ")));

        List<String> tooMany = new ArrayList<>(IntStream.rangeClosed(0, OreImportModels.MAX_SELECTED_GROUPS)
                .mapToObj(index -> "example:ore_" + index)
                .toList());
        assertThrows(
                IllegalArgumentException.class, () -> OreImportSessionValidation.normalizedSelectedGroupIds(tooMany));
    }

    @Test
    void baseProfileValidationUsesTheOriginalBoundAndMessage() {
        assertEquals("vanilla_balanced", OreImportSessionValidation.normalizedBaseProfileId(" vanilla_balanced "));

        var missing = assertThrows(
                IllegalArgumentException.class, () -> OreImportSessionValidation.normalizedBaseProfileId(null));
        var oversized = assertThrows(
                IllegalArgumentException.class,
                () -> OreImportSessionValidation.normalizedBaseProfileId("x".repeat(129)));
        assertEquals("Base profile ID must contain 1-128 characters", missing.getMessage());
        assertEquals("Base profile ID must contain 1-128 characters", oversized.getMessage());
    }

    @Test
    void previewRequestMustUseOnlyDiscoveredGroupsAndThePlannedBase() {
        DiscoveryResult discovery = discovery();
        Plan plan = plan("vanilla_balanced");

        assertTrue(OreImportSessionValidation.validRequest(
                discovery,
                new OreImportSessionService.PreviewRequest("vanilla_balanced", List.of("example:tin")),
                plan));
        assertFalse(OreImportSessionValidation.validRequest(
                discovery,
                new OreImportSessionService.PreviewRequest("vanilla_balanced", List.of("example:unknown")),
                plan));
        assertFalse(OreImportSessionValidation.validRequest(
                discovery, new OreImportSessionService.PreviewRequest("other_profile", List.of("example:tin")), plan));
    }

    private static DiscoveryResult discovery() {
        Candidate candidate = new Candidate(
                "example:tin_ore",
                "minecraft:stone_ore_replaceables",
                HostKind.STONE,
                Evidence.CONVENTIONAL_TAG,
                List.of("c:ores/tin"));
        Group group = new Group("example:tin", "example", "tin", Evidence.CONVENTIONAL_TAG, List.of(candidate), false);
        return new DiscoveryResult(List.of(group), false, 1);
    }

    private static Plan plan(String baseProfileId) {
        return new Plan(
                baseProfileId,
                OrePresets.create(OrePreset.VANILLA_BALANCED),
                List.of(),
                new Workload(Map.of()),
                new Workload(Map.of()),
                new ValidationReport(List.of()));
    }
}
