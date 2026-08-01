package com.nightsta69.delvefold.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.model.GameplayPreset;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.WorldSettingsDocument;
import com.nightsta69.delvefold.config.validation.ConfigIssue;
import com.nightsta69.delvefold.config.validation.ValidationReport;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LiveConfigReloadPolicyTest {
    @Test
    void repositoryFallbackPassesThroughWithoutDuplicatingDiagnostics() {
        ConfigSnapshot active = snapshot(WorldSettingsDocument.uninitialized(), "active");
        ConfigLoadResult fallback = new ConfigLoadResult(
                snapshot(
                        WorldSettingsDocument.uninitialized()
                                .initialize(TerrainMode.FLAT, OrePreset.RICH, GameplayPreset.SAFE),
                        "fallback"),
                true,
                List.of(ConfigIssue.error("json.invalid", "$", "invalid")));

        assertSame(fallback, LiveConfigReloadPolicy.enforce(active, fallback));
    }

    @Test
    void unchangedLifecycleCandidatePassesThroughExactly() {
        WorldSettingsDocument settings = WorldSettingsDocument.uninitialized()
                .initialize(TerrainMode.CAVERN, OrePreset.VANILLA_BALANCED, GameplayPreset.SAFE);
        ConfigSnapshot active = snapshot(settings, "active");
        ConfigLoadResult candidate =
                new ConfigLoadResult(snapshot(settings.withGameplay(settings.gameplay()), "disk"), false, List.of());

        assertSame(candidate, LiveConfigReloadPolicy.enforce(active, candidate));
    }

    @Test
    void changedLifecycleFieldsRetainActiveSnapshotAndAppendOrderedIssues() {
        ConfigSnapshot active = snapshot(WorldSettingsDocument.uninitialized(), "active");
        WorldSettingsDocument initialized =
                active.settings().initialize(TerrainMode.FLAT, OrePreset.VANILLA_BALANCED, GameplayPreset.SAFE);
        ConfigIssue repositoryWarning = ConfigIssue.warning("repository.warning", "$.source", "warning");
        ConfigLoadResult candidate =
                new ConfigLoadResult(snapshot(initialized, "disk"), false, List.of(repositoryWarning));

        ConfigLoadResult rejected = LiveConfigReloadPolicy.enforce(active, candidate);

        assertTrue(rejected.usedFallback());
        assertSame(active, rejected.snapshot());
        assertEquals(
                List.of(
                        "repository.warning",
                        "settings.lifecycle.epoch_locked",
                        "settings.lifecycle.initialized_locked",
                        "settings.lifecycle.terrain_locked",
                        "fallback.last_good"),
                rejected.issues().stream().map(ConfigIssue::code).toList());
        assertEquals("$", rejected.issues().getLast().path());
        assertEquals(
                "Lifecycle-owned settings changed on disk; continuing with the active snapshot",
                rejected.issues().getLast().message());
    }

    @Test
    void absentInitialPublicationCannotRejectARepositoryResult() {
        ConfigLoadResult result =
                new ConfigLoadResult(snapshot(WorldSettingsDocument.uninitialized(), "disk"), false, List.of());

        assertSame(result, LiveConfigReloadPolicy.enforce(null, result));
    }

    private static ConfigSnapshot snapshot(WorldSettingsDocument settings, String hash) {
        return new ConfigSnapshot(OrePresets.empty(), settings, new ValidationReport(List.of()), Instant.EPOCH, hash);
    }
}
