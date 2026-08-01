package com.nightsta69.delvefold.config.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import com.nightsta69.delvefold.config.validation.ConfigIssue;
import com.nightsta69.delvefold.config.validation.ValidationReport;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OreImportSessionServiceTest {
    private static final UUID OWNER = UUID.fromString("55ba9e84-e7f7-43f8-aacf-b9592ba2353d");
    private static final UUID OTHER = UUID.fromString("44687d10-1b24-48bf-8c2b-8842e1db8371");
    private static final OreImportSessionService.SnapshotBinding BINDING =
            new OreImportSessionService.SnapshotBinding(7L, "registry-a", "profile-a");
    private static final DiscoveryResult DISCOVERY = discovery();
    private static final OreImportSessionService.PreviewRequest REQUEST =
            new OreImportSessionService.PreviewRequest("vanilla_balanced", List.of("example:tin"));

    @Test
    void scanIssuanceRequiresARecordedRateLimitAdmission() {
        Fixture fixture = fixture();

        assertThrows(IllegalStateException.class, () -> fixture.service.issueScan(OWNER, BINDING, DISCOVERY));
    }

    @Test
    void tokensAreOpaqueOwnerBoundAndSupersededPerPlayer() {
        Fixture fixture = fixture();
        var first = issueScan(fixture, OWNER);

        assertEquals(43, first.scanToken().length());
        assertFalse(first.scanToken().contains(OWNER.toString()));
        assertEquals(
                OreImportSessionService.Status.NO_ACTIVE_SCAN,
                fixture.service
                        .issuePreview(OTHER, first.scanToken(), BINDING, REQUEST, validPlan())
                        .status());

        var limited = fixture.service.mayIssueScan(OWNER);
        assertFalse(limited.accepted());
        assertEquals(OreImportSessionService.Status.RATE_LIMITED, limited.status());
        fixture.clock.advance(OreImportSessionService.SCAN_COOLDOWN);
        var replacement = issueScan(fixture, OWNER);
        assertNotEquals(first.scanToken(), replacement.scanToken());
        assertEquals(
                OreImportSessionService.Status.INVALID_TOKEN,
                fixture.service
                        .issuePreview(OWNER, first.scanToken(), BINDING, REQUEST, validPlan())
                        .status());
        assertTrue(fixture.service
                .issuePreview(OWNER, replacement.scanToken(), BINDING, REQUEST, validPlan())
                .accepted());
    }

    @Test
    void scanAndPreviewTokensExpireAfterFiveMinutes() {
        Fixture fixture = fixture();
        var scan = issueScan(fixture, OWNER);
        fixture.clock.advance(OreImportSessionService.SESSION_TTL);

        assertEquals(
                OreImportSessionService.Status.EXPIRED,
                fixture.service
                        .issuePreview(OWNER, scan.scanToken(), BINDING, REQUEST, validPlan())
                        .status());

        var freshScan = issueScan(fixture, OWNER);
        var preview = fixture.service
                .issuePreview(OWNER, freshScan.scanToken(), BINDING, REQUEST, validPlan())
                .preview();
        fixture.clock.advance(OreImportSessionService.SESSION_TTL);

        assertEquals(
                OreImportSessionService.Status.EXPIRED,
                fixture.service
                        .consumeCommit(OWNER, preview.commitToken(), BINDING, "imported_tin")
                        .status());
        assertEquals(
                OreImportSessionService.Status.NO_ACTIVE_PREVIEW,
                fixture.service
                        .consumeCommit(OWNER, preview.commitToken(), BINDING, "imported_tin")
                        .status());
    }

    @Test
    void recognizedCommitTokenIsConsumedBeforeEveryMutableStateCheck() {
        assertConsumedAfterRejection(
                new OreImportSessionService.SnapshotBinding(8L, "registry-a", "profile-a"),
                "imported_tin",
                OreImportSessionService.Status.REVISION_CHANGED);
        assertConsumedAfterRejection(
                new OreImportSessionService.SnapshotBinding(7L, "registry-b", "profile-a"),
                "imported_tin",
                OreImportSessionService.Status.REGISTRY_CHANGED);
        assertConsumedAfterRejection(
                new OreImportSessionService.SnapshotBinding(7L, "registry-a", "profile-b"),
                "imported_tin",
                OreImportSessionService.Status.BASE_CHANGED);
        assertConsumedAfterRejection(BINDING, " ", OreImportSessionService.Status.INVALID_REQUEST);
    }

    @Test
    void successfulCommitReturnsOnlyTheBoundServerSidePlanAndRejectsReplay() {
        Fixture fixture = fixture();
        Plan plan = validPlan();
        var scan = issueScan(fixture, OWNER);
        var preview = fixture.service
                .issuePreview(OWNER, scan.scanToken(), BINDING, REQUEST, plan)
                .preview();

        assertEquals(
                OreImportSessionService.Status.NO_ACTIVE_PREVIEW,
                fixture.service
                        .consumeCommit(OTHER, preview.commitToken(), BINDING, "stolen_profile")
                        .status());

        var committed = fixture.service.consumeCommit(OWNER, preview.commitToken(), BINDING, "  imported_tin  ");

        assertTrue(committed.accepted());
        assertSame(plan, committed.plan());
        assertEquals(REQUEST, committed.request());
        assertEquals("imported_tin", committed.targetProfileId());
        assertEquals(
                OreImportSessionService.Status.NO_ACTIVE_PREVIEW,
                fixture.service
                        .consumeCommit(OWNER, preview.commitToken(), BINDING, "another_profile")
                        .status());
    }

    @Test
    void replacingPreviewInvalidatesOldCommitTokenWithoutConsumingTheReplacement() {
        Fixture fixture = fixture();
        var scan = issueScan(fixture, OWNER);
        var first = fixture.service
                .issuePreview(OWNER, scan.scanToken(), BINDING, REQUEST, validPlan())
                .preview();
        var second = fixture.service
                .issuePreview(OWNER, scan.scanToken(), BINDING, REQUEST, validPlan())
                .preview();

        assertEquals(
                OreImportSessionService.Status.INVALID_TOKEN,
                fixture.service
                        .consumeCommit(OWNER, first.commitToken(), BINDING, "imported_tin")
                        .status());
        assertTrue(fixture.service
                .consumeCommit(OWNER, second.commitToken(), BINDING, "imported_tin")
                .accepted());
    }

    @Test
    void previewMustMatchTheScannedGroupsAndBaseProfile() {
        Fixture fixture = fixture();
        var scan = issueScan(fixture, OWNER);
        var unknown = new OreImportSessionService.PreviewRequest("vanilla_balanced", List.of("example:unknown"));
        var wrongBase = new OreImportSessionService.PreviewRequest("other_profile", List.of("example:tin"));

        assertEquals(
                OreImportSessionService.Status.INVALID_REQUEST,
                fixture.service
                        .issuePreview(OWNER, scan.scanToken(), BINDING, unknown, validPlan())
                        .status());
        assertEquals(
                OreImportSessionService.Status.INVALID_REQUEST,
                fixture.service
                        .issuePreview(OWNER, scan.scanToken(), BINDING, wrongBase, validPlan())
                        .status());
    }

    @Test
    void invalidPlanCanBePreviewedButNeverCommitted() {
        Fixture fixture = fixture();
        var scan = issueScan(fixture, OWNER);
        var preview = fixture.service
                .issuePreview(OWNER, scan.scanToken(), BINDING, REQUEST, invalidPlan())
                .preview();

        assertEquals(
                OreImportSessionService.Status.PLAN_INVALID,
                fixture.service
                        .consumeCommit(OWNER, preview.commitToken(), BINDING, "imported_tin")
                        .status());
        assertEquals(
                OreImportSessionService.Status.NO_ACTIVE_PREVIEW,
                fixture.service
                        .consumeCommit(OWNER, preview.commitToken(), BINDING, "imported_tin")
                        .status());
    }

    @Test
    void scanPagingIsOwnerBoundAndDoesNotExtendTheOriginalExpiry() {
        Fixture fixture = fixture();
        var scan = issueScan(fixture, OWNER);

        assertEquals(
                OreImportSessionService.Status.NO_ACTIVE_SCAN,
                fixture.service.accessScan(OTHER, scan.scanToken(), BINDING).status());
        fixture.clock.advance(Duration.ofMinutes(4));
        var accessed = fixture.service.accessScan(OWNER, scan.scanToken(), BINDING);
        assertTrue(accessed.accepted());
        assertSame(DISCOVERY, accessed.scan().discovery());
        assertEquals(scan.expiresAtEpochMillis(), accessed.scan().expiresAtEpochMillis());

        fixture.clock.advance(Duration.ofMinutes(1));
        assertEquals(
                OreImportSessionService.Status.EXPIRED,
                fixture.service.accessScan(OWNER, scan.scanToken(), BINDING).status());
    }

    @Test
    void previewPagingIsOwnerBoundNonConsumingAndDoesNotExtendExpiry() {
        Fixture fixture = fixture();
        Plan plan = validPlan();
        var scan = issueScan(fixture, OWNER);
        var preview = fixture.service
                .issuePreview(OWNER, scan.scanToken(), BINDING, REQUEST, plan)
                .preview();

        assertEquals(
                OreImportSessionService.Status.NO_ACTIVE_PREVIEW,
                fixture.service
                        .accessPreview(OTHER, preview.commitToken(), BINDING)
                        .status());
        var accessed = fixture.service.accessPreview(OWNER, preview.commitToken(), BINDING);
        assertTrue(accessed.accepted());
        assertSame(plan, accessed.preview().plan());
        assertEquals(preview.expiresAtEpochMillis(), accessed.preview().expiresAtEpochMillis());
        assertTrue(fixture.service
                .consumeCommit(OWNER, preview.commitToken(), BINDING, "imported_tin")
                .accepted());

        Fixture expiring = fixture();
        var expiringScan = issueScan(expiring, OWNER);
        var expiringPreview = expiring.service
                .issuePreview(OWNER, expiringScan.scanToken(), BINDING, REQUEST, validPlan())
                .preview();
        expiring.clock.advance(Duration.ofMinutes(4));
        assertTrue(expiring.service
                .accessPreview(OWNER, expiringPreview.commitToken(), BINDING)
                .accepted());
        expiring.clock.advance(Duration.ofMinutes(1));
        assertEquals(
                OreImportSessionService.Status.EXPIRED,
                expiring.service
                        .accessPreview(OWNER, expiringPreview.commitToken(), BINDING)
                        .status());
    }

    @Test
    void pagingRejectsAndInvalidatesStateThatChangedSinceTheScan() {
        Fixture scanFixture = fixture();
        var scan = issueScan(scanFixture, OWNER);
        var changedRevision = new OreImportSessionService.SnapshotBinding(
                8L, BINDING.registryFingerprint(), BINDING.baseContentHash());
        assertEquals(
                OreImportSessionService.Status.REVISION_CHANGED,
                scanFixture
                        .service
                        .accessScan(OWNER, scan.scanToken(), changedRevision)
                        .status());
        assertEquals(
                OreImportSessionService.Status.NO_ACTIVE_SCAN,
                scanFixture.service.accessScan(OWNER, scan.scanToken(), BINDING).status());

        Fixture previewFixture = fixture();
        var previewScan = issueScan(previewFixture, OWNER);
        var preview = previewFixture
                .service
                .issuePreview(OWNER, previewScan.scanToken(), BINDING, REQUEST, validPlan())
                .preview();
        var changedRegistry = new OreImportSessionService.SnapshotBinding(7L, "registry-b", BINDING.baseContentHash());
        assertEquals(
                OreImportSessionService.Status.REGISTRY_CHANGED,
                previewFixture
                        .service
                        .accessPreview(OWNER, preview.commitToken(), changedRegistry)
                        .status());
        assertEquals(
                OreImportSessionService.Status.NO_ACTIVE_PREVIEW,
                previewFixture
                        .service
                        .consumeCommit(OWNER, preview.commitToken(), BINDING, "imported_tin")
                        .status());
    }

    @Test
    void playerAndServerInvalidationRemoveOnlyTheIntendedSessions() {
        Fixture fixture = fixture();
        var ownerScan = issueScan(fixture, OWNER);
        var otherScan = issueScan(fixture, OTHER);

        fixture.service.invalidatePlayer(OWNER);

        assertEquals(
                OreImportSessionService.Status.NO_ACTIVE_SCAN,
                fixture.service
                        .issuePreview(OWNER, ownerScan.scanToken(), BINDING, REQUEST, validPlan())
                        .status());
        assertTrue(fixture.service
                .issuePreview(OTHER, otherScan.scanToken(), BINDING, REQUEST, validPlan())
                .accepted());

        fixture.service.invalidateAll();
        assertEquals(
                OreImportSessionService.Status.NO_ACTIVE_SCAN,
                fixture.service
                        .issuePreview(OTHER, otherScan.scanToken(), BINDING, REQUEST, validPlan())
                        .status());
    }

    private static void assertConsumedAfterRejection(
            OreImportSessionService.SnapshotBinding current,
            String targetProfileId,
            OreImportSessionService.Status expected) {
        Fixture fixture = fixture();
        var scan = issueScan(fixture, OWNER);
        var preview = fixture.service
                .issuePreview(OWNER, scan.scanToken(), BINDING, REQUEST, validPlan())
                .preview();

        assertEquals(
                expected,
                fixture.service
                        .consumeCommit(OWNER, preview.commitToken(), current, targetProfileId)
                        .status());
        assertEquals(
                OreImportSessionService.Status.NO_ACTIVE_PREVIEW,
                fixture.service
                        .consumeCommit(OWNER, preview.commitToken(), BINDING, "retry")
                        .status());
    }

    private static OreImportSessionService.IssuedScan issueScan(Fixture fixture, UUID owner) {
        assertTrue(fixture.service.mayIssueScan(owner).accepted());
        return fixture.service.issueScan(owner, BINDING, DISCOVERY);
    }

    private static Fixture fixture() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-31T12:00:00Z"));
        Queue<String> values = new ArrayDeque<>(List.of(
                "A".repeat(43),
                "B".repeat(43),
                "C".repeat(43),
                "D".repeat(43),
                "E".repeat(43),
                "F".repeat(43),
                "G".repeat(43),
                "H".repeat(43)));
        return new Fixture(clock, new OreImportSessionService(clock, values::remove));
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

    private static Plan validPlan() {
        return plan(new ValidationReport(List.of()));
    }

    private static Plan invalidPlan() {
        return plan(new ValidationReport(List.of(ConfigIssue.error("test.invalid", "$", "Test validation failure"))));
    }

    private static Plan plan(ValidationReport validation) {
        return new Plan(
                "vanilla_balanced",
                OrePresets.create(OrePreset.VANILLA_BALANCED),
                List.of(),
                new Workload(Map.of()),
                new Workload(Map.of()),
                validation);
    }

    private record Fixture(MutableClock clock, OreImportSessionService service) {}

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
