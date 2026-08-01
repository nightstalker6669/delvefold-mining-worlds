package com.nightsta69.delvefold.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.audit.AuditMutation;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.PortalHubSettings;
import com.nightsta69.delvefold.config.model.PortalRoutingMode;
import com.nightsta69.delvefold.config.model.PortalSettings;
import com.nightsta69.delvefold.config.model.WorldSettingsDocument;
import com.nightsta69.delvefold.config.validation.ValidationReport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigAuditPlannerTest {
    @Test
    void sameRevisionContentChangesStillProduceGenericAndSemanticMutations() {
        OreProfileDocument beforeOres = OrePresets.balanced();
        WorldSettingsDocument beforeSettings = WorldSettingsDocument.uninitialized();
        PortalSettings previousPortal = beforeSettings.portal();
        PortalSettings replacementPortal = new PortalSettings(
                previousPortal.enabled(),
                previousPortal.allowFromOverworldOnly(),
                previousPortal.cooldownSeconds(),
                previousPortal.coordinateScale(),
                PortalRoutingMode.CENTRAL_HUB,
                new PortalHubSettings(128, -64, 32));
        WorldSettingsDocument savedSettings = beforeSettings
                .withActiveProfile("rich")
                .withPortal(replacementPortal);
        OreProfileDocument savedOres = new OreProfileDocument(
                OreProfileDocument.CURRENT_SCHEMA_VERSION,
                beforeOres.revision(),
                "rich",
                OrePresets.rich().rules());

        List<AuditMutation> mutations = ConfigAuditPlanner.plan(
                snapshot(beforeOres, beforeSettings), snapshot(savedOres, savedSettings), "console");

        assertEquals(List.of(
                AuditMutation.Operation.CONFIGURATION_ACCEPTED,
                AuditMutation.Operation.CONFIGURATION_ACCEPTED,
                AuditMutation.Operation.PROFILE_ACTIVATED,
                AuditMutation.Operation.PORTAL_ROUTING_CHANGED,
                AuditMutation.Operation.HUB_PROTECTION_CHANGED),
                mutations.stream().map(AuditMutation::operation).toList());
        assertTrue(mutations.stream().allMatch(mutation -> mutation.oldRevision() == 0L));
        assertTrue(mutations.stream().allMatch(mutation -> mutation.newRevision() == 0L));
        assertEquals("profile:rich", mutations.get(2).affectedObject());
        assertEquals("portal:routing", mutations.get(3).affectedObject());
        assertEquals("hub:central_hub", mutations.get(4).affectedObject());
    }

    @Test
    void snapshotMetadataAloneDoesNotDescribeAConfigurationMutation() {
        OreProfileDocument ores = OrePresets.balanced();
        WorldSettingsDocument settings = WorldSettingsDocument.uninitialized();
        ConfigSnapshot before = snapshot(ores, settings);
        ConfigSnapshot reloaded = new ConfigSnapshot(
                ores, settings, new ValidationReport(List.of()), Instant.ofEpochSecond(12), "other-hash");

        assertTrue(ConfigAuditPlanner.plan(before, reloaded, "server").isEmpty());
    }

    @Test
    void profileMutationsPreserveKindObjectAndExactRevisionTransition() {
        OreProfileDocument createdProfile = new OreProfileDocument(
                OreProfileDocument.CURRENT_SCHEMA_VERSION, 0L, "new_profile", List.of());
        OreProfileCatalog.ProfileWriteResult created = new OreProfileCatalog.ProfileWriteResult(
                true, createdProfile, List.of(), "created", -1L);
        AuditMutation createMutation = ConfigAuditPlanner.profileWrite(created, "Alex").getFirst();
        assertEquals(AuditMutation.Operation.PROFILE_CREATED, createMutation.operation());
        assertEquals("profile:new_profile", createMutation.affectedObject());
        assertEquals(-1L, createMutation.oldRevision());
        assertEquals(0L, createMutation.newRevision());

        OreProfileDocument updatedProfile = new OreProfileDocument(
                OreProfileDocument.CURRENT_SCHEMA_VERSION, 5L, "new_profile", List.of());
        OreProfileCatalog.ProfileWriteResult updated = new OreProfileCatalog.ProfileWriteResult(
                true, updatedProfile, List.of(), "updated", 4L);
        AuditMutation updateMutation = ConfigAuditPlanner.profileWrite(updated, "Alex").getFirst();
        assertEquals(AuditMutation.Operation.PROFILE_UPDATED, updateMutation.operation());
        assertEquals(4L, updateMutation.oldRevision());
        assertEquals(5L, updateMutation.newRevision());

        OreProfileCatalog.ProfileDeleteResult deleted =
                new OreProfileCatalog.ProfileDeleteResult(true, 5L);
        AuditMutation deleteMutation = ConfigAuditPlanner.profileDelete(
                "new_profile", deleted, "Alex").getFirst();
        assertEquals(AuditMutation.Operation.PROFILE_DELETED, deleteMutation.operation());
        assertEquals("profile:new_profile", deleteMutation.affectedObject());
        assertEquals(5L, deleteMutation.oldRevision());
        assertEquals(-1L, deleteMutation.newRevision());

        assertTrue(ConfigAuditPlanner.profileWrite(
                OreProfileCatalog.ProfileWriteResult.rejected("rejected"), "Alex").isEmpty());
        assertTrue(ConfigAuditPlanner.profileDelete(
                "new_profile", new OreProfileCatalog.ProfileDeleteResult(false, -1L), "Alex").isEmpty());
    }

    @Test
    void rejectedReloadBranchCannotPublishOrAuditItsFallbackSnapshot() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/nightsta69/delvefold/config/DelvefoldConfigService.java"));
        int start = source.indexOf("public ConfigLoadResult reload()");
        int end = source.indexOf("public ConfigLoadResult validateDisk()", start);
        String reload = source.substring(start, end);

        assertTrue(reload.contains("if (!result.usedFallback())"));
        assertTrue(reload.indexOf("if (!result.usedFallback())")
                        < reload.indexOf("auditSavedConfiguration(before, result.snapshot())"),
                "Only an accepted reload may reach the audit planner");
    }

    private static ConfigSnapshot snapshot(OreProfileDocument ores, WorldSettingsDocument settings) {
        return new ConfigSnapshot(ores, settings, new ValidationReport(List.of()), Instant.EPOCH, "hash");
    }
}
