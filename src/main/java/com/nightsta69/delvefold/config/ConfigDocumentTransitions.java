package com.nightsta69.delvefold.config;

import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.OreRule;
import com.nightsta69.delvefold.config.model.WorldSettingsDocument;
import java.util.List;
import java.util.Objects;

/**
 * Pure schema and optimistic-revision transitions for configuration documents.
 *
 * <p>The configuration service owns locking, validation, persistence, publication, auditing, and event delivery. This
 * collaborator only constructs the immutable candidates that those ordered side effects consume.
 */
final class ConfigDocumentTransitions {
    private ConfigDocumentTransitions() {}

    /**
     * Copies an ore candidate into the current schema at the revision following the active document.
     *
     * @param active currently published ore document
     * @param candidate complete candidate whose profile and rules should be retained
     * @return normalized schema-2 candidate at {@code active.revision() + 1}
     */
    static OreProfileDocument nextOres(OreProfileDocument active, OreProfileDocument candidate) {
        Objects.requireNonNull(candidate, "candidate");
        return nextOres(active, candidate.profile(), candidate.rules());
    }

    /**
     * Constructs an ore candidate without allocating an intermediate profile document.
     *
     * @param active currently published ore document
     * @param profile profile ID to retain
     * @param rules complete ordered replacement rule list
     * @return normalized schema-2 candidate at {@code active.revision() + 1}
     */
    static OreProfileDocument nextOres(OreProfileDocument active, String profile, List<OreRule> rules) {
        Objects.requireNonNull(active, "active");
        return new OreProfileDocument(
                OreProfileDocument.CURRENT_SCHEMA_VERSION, active.revision() + 1L, profile, rules);
    }

    /**
     * Copies a settings candidate into the current schema at the revision following the active document.
     *
     * <p>Every lifecycle, gameplay, portal, identity, guide, and retention value comes from the supplied candidate. The
     * service remains responsible for deciding which fields may change during a live mutation.
     *
     * @param active currently published settings document
     * @param candidate complete candidate whose configuration values should be retained
     * @return normalized schema-2 candidate at {@code active.revision() + 1}
     */
    static WorldSettingsDocument nextSettings(WorldSettingsDocument active, WorldSettingsDocument candidate) {
        Objects.requireNonNull(active, "active");
        Objects.requireNonNull(candidate, "candidate");
        return new WorldSettingsDocument(
                WorldSettingsDocument.CURRENT_SCHEMA_VERSION,
                active.revision() + 1L,
                candidate.generationEpoch(),
                candidate.generationSalt(),
                candidate.lastWorldOperationId(),
                candidate.initialized(),
                candidate.terrainMode(),
                candidate.orePreset(),
                candidate.gameplay(),
                candidate.portal(),
                candidate.activeProfileId(),
                candidate.identity(),
                candidate.guideVisibility(),
                candidate.backupRetention());
    }

    /**
     * Plans the two-document transition required to activate a catalog profile.
     *
     * <p>The selected profile's rules and identity replace the active ore document and active-profile setting. Every
     * other world setting, including backup retention and generation identity, is preserved exactly.
     *
     * @param before coherent currently published snapshot
     * @param selected validated catalog profile selected for activation
     * @return normalized ore and settings candidates, each at its next revision
     */
    static ProfileActivation activateProfile(ConfigSnapshot before, OreProfileDocument selected) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(selected, "selected");
        OreProfileDocument ores = nextOres(before.ores(), selected);
        WorldSettingsDocument settings =
                nextSettings(before.settings(), before.settings().withActiveProfile(selected.profile()));
        return new ProfileActivation(ores, settings);
    }

    /**
     * Immutable pair of canonical documents produced by profile activation planning.
     *
     * @param ores normalized active ore candidate
     * @param settings normalized settings candidate naming the same profile
     */
    record ProfileActivation(OreProfileDocument ores, WorldSettingsDocument settings) {
        /**
         * Creates an activation pair and rejects malformed null collaborators before persistence.
         *
         * @param ores normalized active ore candidate
         * @param settings normalized settings candidate
         */
        ProfileActivation {
            Objects.requireNonNull(ores, "ores");
            Objects.requireNonNull(settings, "settings");
        }
    }
}
