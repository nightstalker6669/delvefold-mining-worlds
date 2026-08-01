package com.nightsta69.delvefold.client.gui;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.model.GameplayPreset;
import com.nightsta69.delvefold.config.model.GameplaySettings;
import com.nightsta69.delvefold.config.model.GeologyTheme;
import com.nightsta69.delvefold.config.model.LandmarkPreset;
import com.nightsta69.delvefold.config.model.PortalHubSettings;
import com.nightsta69.delvefold.config.model.PortalRoutingMode;
import com.nightsta69.delvefold.config.model.PortalSettings;
import com.nightsta69.delvefold.config.model.RenewalSeedMode;
import com.nightsta69.delvefold.config.model.RenewalSettings;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.TerrainVariant;
import com.nightsta69.delvefold.config.model.WorldIdentitySettings;
import com.nightsta69.delvefold.network.model.AdminOperation;
import com.nightsta69.delvefold.network.model.AdminSnapshot;
import com.nightsta69.delvefold.network.model.AdminSnapshot.AdminCapabilities;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardDraftStateTest {
    @Test
    void snapshotInitializationCopiesEveryEditableDomainAndStartsUnarmed() {
        GameplaySettings gameplay = GameplaySettings.fromPreset(GameplayPreset.HOSTILE);
        PortalSettings portal = new PortalSettings(
                false, false, 37, 2.5D, PortalRoutingMode.CENTRAL_HUB, new PortalHubSettings(12, -34, 24));
        WorldIdentitySettings identity = new WorldIdentitySettings(
                "Deep Mine",
                TerrainVariant.EXPANSIVE,
                LandmarkPreset.ABUNDANT,
                true,
                true,
                true,
                GeologyTheme.CRYSTAL,
                new RenewalSettings(true, 7, 45, 123_456L, RenewalSeedMode.ROTATE_ON_RECREATE));
        AdminSnapshot snapshot = snapshot(gameplay, portal, identity);

        DashboardDraftState drafts = DashboardDraftState.from(snapshot);

        assertEquals(gameplay, drafts.gameplay().settings());
        assertEquals(portal, drafts.portal().validatedSettings());
        assertEquals(DashboardDraftState.ProfileDraft.defaults(), drafts.profile());
        assertEquals(identity.displayName(), drafts.identity().displayName());
        assertEquals(identity.landmarkPreset(), drafts.identity().landmarkPreset());
        assertEquals(identity.geologyTheme(), drafts.identity().activeGeologyTheme());
        assertEquals(identity.renewal().enabled(), drafts.identity().renewalEnabled());
        assertEquals(
                Integer.toString(identity.renewal().intervalDays()),
                drafts.identity().renewalDays());
        assertEquals(
                Integer.toString(identity.renewal().warningMinutes()),
                drafts.identity().renewalWarning());
        assertEquals(identity.renewal().seedMode(), drafts.identity().renewalSeedMode());
        assertEquals(snapshot.terrainMode(), drafts.world().terrain());
        assertEquals(identity.terrainVariant(), drafts.world().variant());
        assertEquals(identity.geologyTheme(), drafts.world().geologyTheme());
        assertNull(drafts.world().armedOperation());
    }

    @Test
    void replacingOneDraftPreservesEveryOtherDomainByIdentity() {
        DashboardDraftState original = DashboardDraftState.from(AdminSnapshot.unavailable());
        DashboardDraftState.PortalDraft replacement = original.portal().toggleEnabled();

        DashboardDraftState updated = original.withPortal(replacement);

        assertSame(original.gameplay(), updated.gameplay());
        assertSame(replacement, updated.portal());
        assertSame(original.profile(), updated.profile());
        assertSame(original.identity(), updated.identity());
        assertSame(original.world(), updated.world());
    }

    @Test
    void gameplayToggleChangesOnlyItsNamedFlagAndPresetReplacementIsCanonical() {
        DashboardDraftState.GameplayDraft safe =
                DashboardDraftState.GameplayDraft.from(GameplaySettings.fromPreset(GameplayPreset.SAFE));

        DashboardDraftState.GameplayDraft monsters = safe.with(DashboardDraftState.GameplayToggle.MONSTERS, true);

        assertTrue(monsters.monsters());
        assertEquals(safe.creatures(), monsters.creatures());
        assertEquals(safe.ambient(), monsters.ambient());
        assertEquals(safe.waterCreatures(), monsters.waterCreatures());
        assertEquals(safe.patrols(), monsters.patrols());
        assertEquals(safe.phantoms(), monsters.phantoms());
        assertEquals(
                GameplaySettings.fromPreset(GameplayPreset.NORMAL),
                safe.withPreset(GameplayPreset.NORMAL).settings());
    }

    @Test
    void portalNumericBoundariesMatchServerModelLimits() {
        assertDoesNotThrow(
                () -> portal("1", "0.01", "-29999936", "29999936", "8").validatedSettings());
        assertDoesNotThrow(
                () -> portal("3600", "100.0", "29999936", "-29999936", "256").validatedSettings());

        for (DashboardDraftState.PortalDraft invalid : List.of(
                portal("0", "1", "0", "0", "16"),
                portal("3601", "1", "0", "0", "16"),
                portal("1", "0.009", "0", "0", "16"),
                portal("1", "100.01", "0", "0", "16"),
                portal("1", "NaN", "0", "0", "16"),
                portal("1", "Infinity", "0", "0", "16"),
                portal("1", "1", "29999937", "0", "16"),
                portal("1", "1", "0", "-29999937", "16"),
                portal("1", "1", "0", "0", "7"),
                portal("1", "1", "0", "0", "257"),
                portal("not-a-number", "1", "0", "0", "16"))) {
            assertThrows(IllegalArgumentException.class, invalid::validatedSettings);
        }
    }

    @Test
    void identityValidationHonorsRenewalBoundsAndPermissionPreservation() {
        WorldIdentitySettings current = WorldIdentitySettings.defaults()
                .withRenewal(new RenewalSettings(true, 30, 60, 9_999L, RenewalSeedMode.STABLE));
        DashboardDraftState.IdentityDraft lower = identity("Mine", "1", "1", true);
        DashboardDraftState.IdentityDraft upper = identity("Mine", "3650", "10080", true);

        assertDoesNotThrow(() -> lower.validatedSettings(current, true, 1_000L));
        assertDoesNotThrow(() -> upper.validatedSettings(current, true, 1_000L));
        assertThrows(
                NumberFormatException.class,
                () -> identity(" ", "30", "60", true).validatedSettings(current, true, 1_000L));
        assertThrows(
                NumberFormatException.class,
                () -> identity("Mine", "0", "60", true).validatedSettings(current, true, 1_000L));
        assertThrows(
                NumberFormatException.class,
                () -> identity("Mine", "3651", "60", true).validatedSettings(current, true, 1_000L));
        assertThrows(
                NumberFormatException.class,
                () -> identity("Mine", "30", "0", true).validatedSettings(current, true, 1_000L));
        assertThrows(
                NumberFormatException.class,
                () -> identity("Mine", "30", "10081", true).validatedSettings(current, true, 1_000L));

        DashboardDraftState.IdentityDraft unauthorized = identity("Renamed", "invalid", "invalid", false)
                .withLandmarkPreset(LandmarkPreset.PURE_MINING)
                .withRenewalSeedMode(RenewalSeedMode.ROTATE_ON_RECREATE);
        WorldIdentitySettings preserved = unauthorized.validatedSettings(current, false, 1_000L);
        assertSame(current.renewal(), preserved.renewal());
        assertEquals("Renamed", preserved.displayName());
        assertEquals(LandmarkPreset.PURE_MINING, preserved.landmarkPreset());
        assertFalse(preserved.surveyStations());
        assertFalse(preserved.motherlodes());
        assertFalse(preserved.faultLines());
    }

    @Test
    void identityRenewalDeadlineIsPreservedOrRecalculatedExactlyAsBefore() {
        long now = 50_000L;
        WorldIdentitySettings current = WorldIdentitySettings.defaults()
                .withRenewal(new RenewalSettings(true, 30, 60, 999_999L, RenewalSeedMode.STABLE));

        WorldIdentitySettings sameInterval = identity("Mine", "30", "90", true).validatedSettings(current, true, now);
        WorldIdentitySettings changedInterval =
                identity("Mine", "31", "90", true).validatedSettings(current, true, now);
        WorldIdentitySettings disabled = identity("Mine", "30", "90", false).validatedSettings(current, true, now);

        assertEquals(999_999L, sameInterval.renewal().nextRenewalAtEpochMillis());
        assertEquals(now + 31L * 86_400_000L, changedInterval.renewal().nextRenewalAtEpochMillis());
        assertEquals(0L, disabled.renewal().nextRenewalAtEpochMillis());
    }

    @Test
    void switchingDestructiveActionsResetsConfirmationAndUsesCurrentRecreationChoices() {
        DashboardDraftState.WorldDraft initial =
                DashboardDraftState.from(AdminSnapshot.unavailable()).world();

        DashboardDraftState.ArmDecision deleteArmed = initial.arm(AdminOperation.DELETE_WORLD);
        assertNull(deleteArmed.confirmation());
        assertEquals(
                "DELETE", deleteArmed.draft().arm(AdminOperation.DELETE_WORLD).confirmation());

        DashboardDraftState.WorldDraft selected = deleteArmed
                .draft()
                .withTerrain(TerrainMode.CAVERN)
                .withVariant(TerrainVariant.EXPANSIVE)
                .withGeologyTheme(GeologyTheme.VOLCANIC);
        DashboardDraftState.ArmDecision recreateArmed = selected.arm(AdminOperation.RECREATE_WORLD);
        assertNull(recreateArmed.confirmation());
        assertEquals(AdminOperation.RECREATE_WORLD, recreateArmed.draft().armedOperation());
        assertEquals(
                "RECREATE:CAVERN:EXPANSIVE:VOLCANIC",
                recreateArmed.draft().arm(AdminOperation.RECREATE_WORLD).confirmation());

        DashboardDraftState.ArmDecision cancelArmed = recreateArmed.draft().arm(AdminOperation.CANCEL_PENDING_RESET);
        assertNull(cancelArmed.confirmation());
        assertEquals(
                "CANCEL",
                cancelArmed.draft().arm(AdminOperation.CANCEL_PENDING_RESET).confirmation());
    }

    @Test
    void selectingAnotherProfileResetsDeleteConfirmationToThatProfile() {
        DashboardDraftState.ProfileDraft draft = DashboardDraftState.ProfileDraft.defaults();
        DashboardDraftState.DeleteDecision first = draft.armDelete("first");
        DashboardDraftState.DeleteDecision second = first.draft().armDelete("second");

        assertFalse(first.confirmed());
        assertFalse(second.confirmed());
        assertEquals("second", second.draft().armedDeleteId());
        assertTrue(second.draft().armDelete("second").confirmed());
    }

    private static DashboardDraftState.PortalDraft portal(
            String cooldown, String scale, String x, String z, String radius) {
        return new DashboardDraftState.PortalDraft(
                true, true, cooldown, scale, PortalRoutingMode.COORDINATE_LINKED, x, z, radius);
    }

    private static DashboardDraftState.IdentityDraft identity(
            String name, String days, String warning, boolean enabled) {
        return new DashboardDraftState.IdentityDraft(
                name, LandmarkPreset.BALANCED, GeologyTheme.CLASSIC, enabled, days, warning, RenewalSeedMode.STABLE);
    }

    private static AdminSnapshot snapshot(
            GameplaySettings gameplay, PortalSettings portal, WorldIdentitySettings identity) {
        return new AdminSnapshot(
                4L,
                7L,
                true,
                true,
                TerrainMode.CAVERN,
                null,
                gameplay,
                portal,
                identity,
                new AdminCapabilities(true, true, true, true, true),
                "rich",
                List.of(),
                List.of(),
                "portal",
                "world",
                AdminSnapshot.PendingOperation.NONE,
                List.of(),
                0,
                0,
                List.of());
    }
}
