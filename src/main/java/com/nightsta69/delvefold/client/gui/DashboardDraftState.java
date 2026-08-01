package com.nightsta69.delvefold.client.gui;

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
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Immutable collection of dashboard edit drafts kept separate from the server-authoritative snapshot.
 *
 * <p>Widget callbacks replace only the affected draft. Validation produces complete model values but never sends a
 * payload or mutates the snapshot; the screen retains authority over revisions, capabilities, confirmations, and
 * network delivery.
 *
 * @param gameplay editable natural-spawning draft
 * @param portal editable portal-routing draft
 * @param profile transient profile-control state
 * @param identity editable world identity draft
 * @param world recreation choices and destructive confirmation state
 */
record DashboardDraftState(
        GameplayDraft gameplay, PortalDraft portal, ProfileDraft profile, IdentityDraft identity, WorldDraft world) {
    private static final long MILLIS_PER_DAY = 86_400_000L;

    DashboardDraftState {
        Objects.requireNonNull(gameplay, "gameplay");
        Objects.requireNonNull(portal, "portal");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(world, "world");
    }

    /**
     * Creates fresh local drafts from one immutable server snapshot.
     *
     * @param snapshot server-authoritative dashboard state
     * @return initialized drafts with no destructive action armed
     */
    static DashboardDraftState from(AdminSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return new DashboardDraftState(
                GameplayDraft.from(snapshot.gameplay()),
                PortalDraft.from(snapshot.portal()),
                ProfileDraft.defaults(),
                IdentityDraft.from(snapshot.identity()),
                WorldDraft.from(snapshot));
    }

    DashboardDraftState withGameplay(GameplayDraft replacement) {
        return new DashboardDraftState(replacement, portal, profile, identity, world);
    }

    DashboardDraftState withPortal(PortalDraft replacement) {
        return new DashboardDraftState(gameplay, replacement, profile, identity, world);
    }

    DashboardDraftState withProfile(ProfileDraft replacement) {
        return new DashboardDraftState(gameplay, portal, replacement, identity, world);
    }

    DashboardDraftState withIdentity(IdentityDraft replacement) {
        return new DashboardDraftState(gameplay, portal, profile, replacement, world);
    }

    DashboardDraftState withWorld(WorldDraft replacement) {
        return new DashboardDraftState(gameplay, portal, profile, identity, replacement);
    }

    /** Editable gameplay policy with stable named toggles for widget callbacks. */
    record GameplayDraft(
            GameplayPreset preset,
            boolean monsters,
            boolean creatures,
            boolean ambient,
            boolean waterCreatures,
            boolean patrols,
            boolean phantoms) {
        static GameplayDraft from(GameplaySettings settings) {
            Objects.requireNonNull(settings, "settings");
            return new GameplayDraft(
                    settings.preset(),
                    settings.monsters(),
                    settings.creatures(),
                    settings.ambient(),
                    settings.waterCreatures(),
                    settings.patrols(),
                    settings.phantoms());
        }

        GameplayDraft withPreset(GameplayPreset replacement) {
            return from(GameplaySettings.fromPreset(replacement));
        }

        boolean value(GameplayToggle toggle) {
            return switch (toggle) {
                case MONSTERS -> monsters;
                case CREATURES -> creatures;
                case AMBIENT -> ambient;
                case WATER_CREATURES -> waterCreatures;
                case PATROLS -> patrols;
                case PHANTOMS -> phantoms;
            };
        }

        GameplayDraft with(GameplayToggle toggle, boolean value) {
            return new GameplayDraft(
                    preset,
                    toggle == GameplayToggle.MONSTERS ? value : monsters,
                    toggle == GameplayToggle.CREATURES ? value : creatures,
                    toggle == GameplayToggle.AMBIENT ? value : ambient,
                    toggle == GameplayToggle.WATER_CREATURES ? value : waterCreatures,
                    toggle == GameplayToggle.PATROLS ? value : patrols,
                    toggle == GameplayToggle.PHANTOMS ? value : phantoms);
        }

        GameplaySettings settings() {
            return new GameplaySettings(preset, monsters, creatures, ambient, waterCreatures, patrols, phantoms);
        }
    }

    /** Stable identifiers for gameplay toggle widgets. */
    enum GameplayToggle {
        MONSTERS,
        CREATURES,
        AMBIENT,
        WATER_CREATURES,
        PATROLS,
        PHANTOMS
    }

    /** Editable portal values retained as text until an explicit save validates them. */
    record PortalDraft(
            boolean enabled,
            boolean overworldOnly,
            String cooldown,
            String scale,
            PortalRoutingMode routingMode,
            String hubX,
            String hubZ,
            String protectionRadius) {
        PortalDraft {
            Objects.requireNonNull(cooldown, "cooldown");
            Objects.requireNonNull(scale, "scale");
            Objects.requireNonNull(routingMode, "routingMode");
            Objects.requireNonNull(hubX, "hubX");
            Objects.requireNonNull(hubZ, "hubZ");
            Objects.requireNonNull(protectionRadius, "protectionRadius");
        }

        static PortalDraft from(PortalSettings portal) {
            Objects.requireNonNull(portal, "portal");
            return new PortalDraft(
                    portal.enabled(),
                    portal.allowFromOverworldOnly(),
                    Integer.toString(portal.cooldownSeconds()),
                    Double.toString(portal.coordinateScale()),
                    portal.routingMode(),
                    Integer.toString(portal.hub().x()),
                    Integer.toString(portal.hub().z()),
                    Integer.toString(portal.hub().protectionRadius()));
        }

        PortalDraft toggleEnabled() {
            return new PortalDraft(!enabled, overworldOnly, cooldown, scale, routingMode, hubX, hubZ, protectionRadius);
        }

        PortalDraft toggleOverworldOnly() {
            return new PortalDraft(enabled, !overworldOnly, cooldown, scale, routingMode, hubX, hubZ, protectionRadius);
        }

        PortalDraft withRoutingMode(PortalRoutingMode replacement) {
            return new PortalDraft(enabled, overworldOnly, cooldown, scale, replacement, hubX, hubZ, protectionRadius);
        }

        PortalDraft withCooldown(String replacement) {
            return new PortalDraft(
                    enabled, overworldOnly, replacement, scale, routingMode, hubX, hubZ, protectionRadius);
        }

        PortalDraft withScale(String replacement) {
            return new PortalDraft(
                    enabled, overworldOnly, cooldown, replacement, routingMode, hubX, hubZ, protectionRadius);
        }

        PortalDraft withHubX(String replacement) {
            return new PortalDraft(
                    enabled, overworldOnly, cooldown, scale, routingMode, replacement, hubZ, protectionRadius);
        }

        PortalDraft withHubZ(String replacement) {
            return new PortalDraft(
                    enabled, overworldOnly, cooldown, scale, routingMode, hubX, replacement, protectionRadius);
        }

        PortalDraft withProtectionRadius(String replacement) {
            return new PortalDraft(enabled, overworldOnly, cooldown, scale, routingMode, hubX, hubZ, replacement);
        }

        PortalSettings validatedSettings() {
            int parsedCooldown = Integer.parseInt(cooldown.trim());
            double parsedScale = Double.parseDouble(scale.trim());
            int parsedHubX = Integer.parseInt(hubX.trim());
            int parsedHubZ = Integer.parseInt(hubZ.trim());
            int parsedProtectionRadius = Integer.parseInt(protectionRadius.trim());
            if (parsedCooldown < 1
                    || parsedCooldown > 3600
                    || !Double.isFinite(parsedScale)
                    || parsedScale < 0.01D
                    || parsedScale > 100.0D) {
                throw new NumberFormatException();
            }
            return new PortalSettings(
                    enabled,
                    overworldOnly,
                    parsedCooldown,
                    parsedScale,
                    routingMode,
                    new PortalHubSettings(parsedHubX, parsedHubZ, parsedProtectionRadius));
        }
    }

    /** Transient local state for the profile list and profile-transfer controls. */
    record ProfileDraft(String name, String armedDeleteId, boolean overwrite) {
        ProfileDraft {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(armedDeleteId, "armedDeleteId");
        }

        static ProfileDraft defaults() {
            return new ProfileDraft("my_profile", "", false);
        }

        ProfileDraft withName(String replacement) {
            return new ProfileDraft(replacement, armedDeleteId, overwrite);
        }

        ProfileDraft withOverwrite(boolean replacement) {
            return new ProfileDraft(name, armedDeleteId, replacement);
        }

        DeleteDecision armDelete(String profileId) {
            if (!profileId.equals(armedDeleteId)) {
                return new DeleteDecision(new ProfileDraft(name, profileId, overwrite), false);
            }
            return new DeleteDecision(this, true);
        }
    }

    /** Result of one profile-delete arming click. */
    record DeleteDecision(ProfileDraft draft, boolean confirmed) {}

    /** Editable identity values retained as text until an explicit save validates them. */
    record IdentityDraft(
            String displayName,
            LandmarkPreset landmarkPreset,
            GeologyTheme activeGeologyTheme,
            boolean renewalEnabled,
            String renewalDays,
            String renewalWarning,
            RenewalSeedMode renewalSeedMode) {
        IdentityDraft {
            Objects.requireNonNull(displayName, "displayName");
            Objects.requireNonNull(landmarkPreset, "landmarkPreset");
            Objects.requireNonNull(activeGeologyTheme, "activeGeologyTheme");
            Objects.requireNonNull(renewalDays, "renewalDays");
            Objects.requireNonNull(renewalWarning, "renewalWarning");
            Objects.requireNonNull(renewalSeedMode, "renewalSeedMode");
        }

        static IdentityDraft from(WorldIdentitySettings identity) {
            Objects.requireNonNull(identity, "identity");
            return new IdentityDraft(
                    identity.displayName(),
                    identity.landmarkPreset(),
                    identity.geologyTheme(),
                    identity.renewal().enabled(),
                    Integer.toString(identity.renewal().intervalDays()),
                    Integer.toString(identity.renewal().warningMinutes()),
                    identity.renewal().seedMode());
        }

        IdentityDraft withDisplayName(String replacement) {
            return new IdentityDraft(
                    replacement,
                    landmarkPreset,
                    activeGeologyTheme,
                    renewalEnabled,
                    renewalDays,
                    renewalWarning,
                    renewalSeedMode);
        }

        IdentityDraft withLandmarkPreset(LandmarkPreset replacement) {
            return new IdentityDraft(
                    displayName,
                    replacement,
                    activeGeologyTheme,
                    renewalEnabled,
                    renewalDays,
                    renewalWarning,
                    renewalSeedMode);
        }

        IdentityDraft toggleRenewal() {
            return new IdentityDraft(
                    displayName,
                    landmarkPreset,
                    activeGeologyTheme,
                    !renewalEnabled,
                    renewalDays,
                    renewalWarning,
                    renewalSeedMode);
        }

        IdentityDraft withRenewalDays(String replacement) {
            return new IdentityDraft(
                    displayName,
                    landmarkPreset,
                    activeGeologyTheme,
                    renewalEnabled,
                    replacement,
                    renewalWarning,
                    renewalSeedMode);
        }

        IdentityDraft withRenewalWarning(String replacement) {
            return new IdentityDraft(
                    displayName,
                    landmarkPreset,
                    activeGeologyTheme,
                    renewalEnabled,
                    renewalDays,
                    replacement,
                    renewalSeedMode);
        }

        IdentityDraft withRenewalSeedMode(RenewalSeedMode replacement) {
            return new IdentityDraft(
                    displayName,
                    landmarkPreset,
                    activeGeologyTheme,
                    renewalEnabled,
                    renewalDays,
                    renewalWarning,
                    replacement);
        }

        WorldIdentitySettings validatedSettings(
                WorldIdentitySettings current, boolean canManageRenewal, long nowEpochMillis) {
            Objects.requireNonNull(current, "current");
            int days = canManageRenewal
                    ? Integer.parseInt(renewalDays.trim())
                    : current.renewal().intervalDays();
            int warning = canManageRenewal
                    ? Integer.parseInt(renewalWarning.trim())
                    : current.renewal().warningMinutes();
            if (displayName.isBlank()
                    || (canManageRenewal && (days < 1 || days > 3650 || warning < 1 || warning > 10080))) {
                throw new NumberFormatException();
            }
            boolean landmarks = landmarkPreset != LandmarkPreset.PURE_MINING;
            long next = renewalEnabled
                    ? (current.renewal().enabled() && current.renewal().intervalDays() == days
                            ? current.renewal().nextRenewalAtEpochMillis()
                            : nowEpochMillis + days * MILLIS_PER_DAY)
                    : 0L;
            RenewalSettings renewal = canManageRenewal
                    ? new RenewalSettings(renewalEnabled, days, warning, next, renewalSeedMode)
                    : current.renewal();
            return current.withDisplayName(displayName)
                    .withLandmarks(landmarkPreset, landmarks, landmarks, landmarks)
                    .withRenewal(renewal);
        }
    }

    /** Recreation choices plus the currently armed lifecycle action. */
    record WorldDraft(
            TerrainMode terrain,
            TerrainVariant variant,
            GeologyTheme geologyTheme,
            @Nullable AdminOperation armedOperation) {
        WorldDraft {
            Objects.requireNonNull(terrain, "terrain");
            Objects.requireNonNull(variant, "variant");
            Objects.requireNonNull(geologyTheme, "geologyTheme");
        }

        static WorldDraft from(AdminSnapshot snapshot) {
            return new WorldDraft(
                    snapshot.terrainMode(),
                    snapshot.identity().terrainVariant(),
                    snapshot.identity().geologyTheme(),
                    null);
        }

        WorldDraft withTerrain(TerrainMode replacement) {
            return new WorldDraft(replacement, variant, geologyTheme, armedOperation);
        }

        WorldDraft withVariant(TerrainVariant replacement) {
            return new WorldDraft(terrain, replacement, geologyTheme, armedOperation);
        }

        WorldDraft withGeologyTheme(GeologyTheme replacement) {
            return new WorldDraft(terrain, variant, replacement, armedOperation);
        }

        ArmDecision arm(AdminOperation operation) {
            Objects.requireNonNull(operation, "operation");
            if (armedOperation != operation) {
                return new ArmDecision(new WorldDraft(terrain, variant, geologyTheme, operation), null);
            }
            return new ArmDecision(this, confirmation(operation));
        }

        private String confirmation(AdminOperation operation) {
            return switch (operation) {
                case DELETE_WORLD -> "DELETE";
                case RECREATE_WORLD ->
                    "RECREATE:" + terrain.serializedName().toUpperCase(Locale.ROOT) + ":"
                            + variant.serializedName().toUpperCase(Locale.ROOT) + ":"
                            + geologyTheme.serializedName().toUpperCase(Locale.ROOT);
                case CANCEL_PENDING_RESET -> "CANCEL";
                default -> "";
            };
        }
    }

    /** Result of one lifecycle action click; a null confirmation means the action was only armed. */
    record ArmDecision(WorldDraft draft, @Nullable String confirmation) {}
}
