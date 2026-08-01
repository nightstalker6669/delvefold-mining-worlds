package com.nightsta69.delvefold.config;

import com.nightsta69.delvefold.config.analysis.OreProfileForecast;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.world.feature.MinecraftOreProfileForecastBuilder;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.RegistryAccess;
import org.jspecify.annotations.Nullable;

/**
 * Save-local profile-catalog adapter used behind {@link DelvefoldConfigService}'s serialized facade.
 *
 * <p>This collaborator performs no locking, authorization, runtime publication, auditing, or event delivery. Callers
 * must hold the service mutation lock for filesystem-backed operations. Read-only active-profile selection returns the
 * already published immutable document without touching disk.
 */
final class ProfileCatalogOperations {
    private final OreProfileCatalog catalog;

    /**
     * Creates an adapter for one save-local catalog.
     *
     * @param catalog catalog bound to the active save and registry-validation view
     */
    ProfileCatalogOperations(OreProfileCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    /**
     * Lists all profile summaries.
     *
     * @return immutable summaries in catalog order
     * @throws IOException if safe directory preparation or enumeration fails
     */
    List<OreProfileCatalog.ProfileSummary> list() throws IOException {
        return catalog.list();
    }

    /**
     * Selects an active in-memory profile or loads another catalog profile.
     *
     * @param snapshot coherent active configuration snapshot
     * @param id requested profile ID, or null/blank for the active profile
     * @return active immutable document or a validated catalog document
     * @throws IOException if a non-active catalog profile cannot be loaded
     */
    OreProfileDocument load(ConfigSnapshot snapshot, @Nullable String id) throws IOException {
        Objects.requireNonNull(snapshot, "snapshot");
        String selected = id == null || id.isBlank() ? snapshot.ores().profile() : id.trim();
        if (selected.equals(snapshot.ores().profile())) {
            return snapshot.ores();
        }
        return catalog.load(selected);
    }

    /**
     * Loads a profile strictly through catalog precedence, even if its ID matches the active document.
     *
     * @param id local, ecosystem, or bundled profile ID
     * @return validated catalog document
     * @throws IOException if the profile cannot be loaded
     */
    OreProfileDocument loadCatalog(String id) throws IOException {
        return catalog.load(id);
    }

    /**
     * Builds a bounded read-only forecast from a coherent configuration snapshot.
     *
     * @param snapshot coherent current configuration snapshot
     * @param registries active server registry view
     * @param id requested profile ID, or null/blank for the active settings profile
     * @param page requested zero-based page
     * @param pageSize requested rules per page
     * @return immutable bounded forecast
     * @throws IOException if a non-active profile cannot be loaded
     */
    OreProfileForecast forecast(
            ConfigSnapshot snapshot, RegistryAccess registries, @Nullable String id, int page, int pageSize)
            throws IOException {
        Objects.requireNonNull(snapshot, "snapshot");
        String selected = id == null || id.isBlank() ? snapshot.settings().activeProfileId() : id.trim();
        OreProfileDocument profile = load(snapshot, selected);
        return MinecraftOreProfileForecastBuilder.build(
                selected,
                profile,
                snapshot.settings().initialized() ? snapshot.settings().terrainMode() : null,
                registries,
                page,
                pageSize);
    }

    /**
     * Saves the active ore document under an inactive local profile ID.
     *
     * @param snapshot coherent current configuration snapshot
     * @param id destination local profile ID
     * @param overwrite whether an existing local profile may be replaced
     * @return catalog write result
     * @throws IOException if destination inspection or persistence fails
     */
    OreProfileCatalog.ProfileWriteResult saveCurrent(ConfigSnapshot snapshot, String id, boolean overwrite)
            throws IOException {
        return catalog.saveAs(id, snapshot.ores(), overwrite);
    }

    /**
     * Saves a bundled preset under an inactive local profile ID.
     *
     * @param id destination local profile ID
     * @param preset bundled source preset
     * @param overwrite whether an existing local profile may be replaced
     * @return catalog write result
     * @throws IOException if destination inspection or persistence fails
     */
    OreProfileCatalog.ProfileWriteResult createFromPreset(String id, OrePreset preset, boolean overwrite)
            throws IOException {
        return catalog.saveAs(id, OrePresets.create(preset), overwrite);
    }

    /**
     * Creates a new local profile without overwriting or activating an existing entry.
     *
     * @param id destination local profile ID
     * @param source server-authored source profile
     * @return catalog write result
     * @throws IOException if catalog preparation or persistence fails
     */
    OreProfileCatalog.ProfileWriteResult createNew(String id, OreProfileDocument source) throws IOException {
        return catalog.createNew(id, source);
    }

    /**
     * Loads one catalog profile and saves its rules under another inactive local ID.
     *
     * @param sourceId source catalog profile ID
     * @param targetId destination local profile ID
     * @param overwrite whether an existing local destination may be replaced
     * @return catalog write result
     * @throws IOException if source loading, destination inspection, or persistence fails
     */
    OreProfileCatalog.ProfileWriteResult duplicate(String sourceId, String targetId, boolean overwrite)
            throws IOException {
        return catalog.saveAs(targetId, catalog.load(sourceId), overwrite);
    }

    /**
     * Deletes one local profile and reports its prior revision.
     *
     * @param id normalized local profile ID
     * @return deletion result
     * @throws IOException if the target is malformed, unsafe, or cannot be removed
     */
    OreProfileCatalog.ProfileDeleteResult deleteLocal(String id) throws IOException {
        return catalog.deleteLocalWithRevision(id);
    }

    /**
     * Imports a bounded save-local transfer file.
     *
     * @param fileName simple transfer filename
     * @param id destination local profile ID
     * @param overwrite whether an existing local destination may be replaced
     * @return catalog write result
     * @throws IOException if source or destination access fails
     */
    OreProfileCatalog.ProfileWriteResult importFile(String fileName, String id, boolean overwrite) throws IOException {
        return catalog.importFile(fileName, id, overwrite);
    }

    /**
     * Imports bounded strict profile JSON.
     *
     * @param id destination local profile ID
     * @param json complete schema-2 profile JSON
     * @param overwrite whether an existing local destination may be replaced
     * @return catalog write result
     * @throws IOException if destination inspection or persistence fails
     */
    OreProfileCatalog.ProfileWriteResult importJson(String id, String json, boolean overwrite) throws IOException {
        return catalog.importJson(id, json, overwrite);
    }

    /**
     * Serializes one profile without writing disk.
     *
     * @param id catalog profile ID
     * @return canonical pretty-printed JSON
     * @throws IOException if the profile cannot be loaded and validated
     */
    String exportJson(String id) throws IOException {
        return catalog.exportJson(id);
    }

    /**
     * Writes one profile to a bounded save-local export file.
     *
     * @param id catalog profile ID
     * @param fileName simple destination filename
     * @return normalized exported path
     * @throws IOException if loading, path validation, or persistence fails
     */
    Path exportFile(String id, String fileName) throws IOException {
        return catalog.exportFile(id, fileName);
    }
}
