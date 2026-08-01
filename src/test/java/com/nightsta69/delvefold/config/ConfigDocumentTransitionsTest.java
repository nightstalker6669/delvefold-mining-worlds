package com.nightsta69.delvefold.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.nightsta69.delvefold.config.model.BackupRetentionSettings;
import com.nightsta69.delvefold.config.model.GameplayPreset;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.PortalSettings;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.WorldSettingsDocument;
import com.nightsta69.delvefold.config.validation.ValidationReport;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigDocumentTransitionsTest {
    private static final BackupRetentionSettings RETENTION = new BackupRetentionSettings(true, 9, 45, 8_000_000_000L);

    @Test
    void oreCandidatesUseCurrentSchemaAndNextActiveRevision() {
        OreProfileDocument active =
                new OreProfileDocument(2, 41L, "active", OrePresets.empty().rules());
        OreProfileDocument candidate = new OreProfileDocument(
                91, 800L, "replacement", OrePresets.rich().rules());

        OreProfileDocument transitioned = ConfigDocumentTransitions.nextOres(active, candidate);

        assertEquals(OreProfileDocument.CURRENT_SCHEMA_VERSION, transitioned.schemaVersion());
        assertEquals(42L, transitioned.revision());
        assertEquals(candidate.profile(), transitioned.profile());
        assertEquals(candidate.rules(), transitioned.rules());
    }

    @Test
    void directRuleTransitionAvoidsAnIntermediateDocumentWithoutChangingOutput() {
        OreProfileDocument active =
                new OreProfileDocument(2, 8L, "active", OrePresets.empty().rules());

        OreProfileDocument transitioned = ConfigDocumentTransitions.nextOres(
                active, "active", OrePresets.rich().rules());

        assertEquals(active.nextRevision(OrePresets.rich().rules(), "active"), transitioned);
    }

    @Test
    void settingsCandidatesPreserveEveryCandidateValueExceptSchemaAndRevision() {
        WorldSettingsDocument active = WorldSettingsDocument.uninitialized()
                .withBackupRetention(RETENTION)
                .initialize(TerrainMode.FLAT, OrePreset.VANILLA_BALANCED, GameplayPreset.SAFE);
        WorldSettingsDocument candidate = new WorldSettingsDocument(
                99,
                700L,
                active.generationEpoch(),
                active.generationSalt(),
                active.lastWorldOperationId(),
                active.initialized(),
                active.terrainMode(),
                active.orePreset(),
                active.gameplay(),
                new PortalSettings(false, false, 12, 2.5D),
                "replacement",
                active.identity(),
                active.guideVisibility(),
                RETENTION);

        WorldSettingsDocument transitioned = ConfigDocumentTransitions.nextSettings(active, candidate);

        assertEquals(WorldSettingsDocument.CURRENT_SCHEMA_VERSION, transitioned.schemaVersion());
        assertEquals(active.revision() + 1L, transitioned.revision());
        assertEquals(candidate.generationEpoch(), transitioned.generationEpoch());
        assertEquals(candidate.generationSalt(), transitioned.generationSalt());
        assertEquals(candidate.lastWorldOperationId(), transitioned.lastWorldOperationId());
        assertEquals(candidate.initialized(), transitioned.initialized());
        assertEquals(candidate.terrainMode(), transitioned.terrainMode());
        assertEquals(candidate.orePreset(), transitioned.orePreset());
        assertEquals(candidate.gameplay(), transitioned.gameplay());
        assertEquals(candidate.portal(), transitioned.portal());
        assertEquals(candidate.activeProfileId(), transitioned.activeProfileId());
        assertEquals(candidate.identity(), transitioned.identity());
        assertEquals(candidate.guideVisibility(), transitioned.guideVisibility());
        assertSame(RETENTION, transitioned.backupRetention());
    }

    @Test
    void profileActivationChangesOnlyProfileRulesIdentityAndBothRevisions() {
        WorldSettingsDocument settings = WorldSettingsDocument.uninitialized()
                .withBackupRetention(RETENTION)
                .initialize(TerrainMode.CAVERN, OrePreset.VANILLA_BALANCED, GameplayPreset.HOSTILE);
        OreProfileDocument active = new OreProfileDocument(
                2, 12L, "vanilla_balanced", OrePresets.empty().rules());
        ConfigSnapshot before = snapshot(active, settings);
        OreProfileDocument selected =
                new OreProfileDocument(73, 999L, "modded", OrePresets.rich().rules());

        ConfigDocumentTransitions.ProfileActivation activation =
                ConfigDocumentTransitions.activateProfile(before, selected);

        assertEquals(
                OreProfileDocument.CURRENT_SCHEMA_VERSION, activation.ores().schemaVersion());
        assertEquals(13L, activation.ores().revision());
        assertEquals(selected.profile(), activation.ores().profile());
        assertEquals(selected.rules(), activation.ores().rules());
        assertEquals(
                WorldSettingsDocument.CURRENT_SCHEMA_VERSION,
                activation.settings().schemaVersion());
        assertEquals(settings.revision() + 1L, activation.settings().revision());
        assertEquals(selected.profile(), activation.settings().activeProfileId());
        assertEquals(settings.generationEpoch(), activation.settings().generationEpoch());
        assertEquals(settings.generationSalt(), activation.settings().generationSalt());
        assertEquals(settings.lastWorldOperationId(), activation.settings().lastWorldOperationId());
        assertEquals(settings.initialized(), activation.settings().initialized());
        assertEquals(settings.terrainMode(), activation.settings().terrainMode());
        assertEquals(settings.orePreset(), activation.settings().orePreset());
        assertEquals(settings.gameplay(), activation.settings().gameplay());
        assertEquals(settings.portal(), activation.settings().portal());
        assertEquals(settings.identity(), activation.settings().identity());
        assertEquals(settings.guideVisibility(), activation.settings().guideVisibility());
        assertSame(RETENTION, activation.settings().backupRetention());
    }

    @Test
    void releasedRevisionArithmeticStillWrapsAtTheLongBoundaryForValidationToReject() {
        OreProfileDocument ores = new OreProfileDocument(2, Long.MAX_VALUE, "active", List.of());
        WorldSettingsDocument settings = new WorldSettingsDocument(
                2, Long.MAX_VALUE, 0L, 0L, "", false, null, OrePreset.EMPTY, null, null, "empty", null, null, null);

        assertEquals(
                Long.MIN_VALUE, ConfigDocumentTransitions.nextOres(ores, ores).revision());
        assertEquals(
                Long.MIN_VALUE,
                ConfigDocumentTransitions.nextSettings(settings, settings).revision());
    }

    private static ConfigSnapshot snapshot(OreProfileDocument ores, WorldSettingsDocument settings) {
        return new ConfigSnapshot(ores, settings, new ValidationReport(List.of()), Instant.EPOCH, "test");
    }
}
