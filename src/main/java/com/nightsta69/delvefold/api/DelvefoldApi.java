package com.nightsta69.delvefold.api;

import com.nightsta69.delvefold.config.DelvefoldConfigService;
import com.nightsta69.delvefold.config.EcosystemProfileRegistry;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.WorldSettingsDocument;
import com.nightsta69.delvefold.guide.GuideSnapshot;
import com.nightsta69.delvefold.guide.GuideSnapshotService;
import com.nightsta69.delvefold.world.DelvefoldWorldgen;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Stable Delvefold 1.x integration surface. New 1.x APIs will remain backward compatible. */
public final class DelvefoldApi {
    /** Public API compatibility level for integrations targeting the Delvefold 1.x series. */
    public static final int API_VERSION = 1;

    /** Prevents utility-class instantiation. */
    private DelvefoldApi() {}

    /**
     * Returns the initialized mining world advertised by the active server configuration.
     *
     * @return an immutable world view, or an empty optional while no configuration is loaded or initialized
     */
    public static Optional<MiningWorldView> activeWorld() {
        try {
            return worldView(DelvefoldConfigService.get().snapshot().settings());
        } catch (IllegalStateException ignored) {
            return Optional.empty();
        }
    }

    /**
     * Returns the bounded, read-only player guide for the active server.
     *
     * <p>The guide intentionally excludes seeds, horizontal coordinates, filesystem paths, confirmation data, and
     * administration diagnostics.
     *
     * @return the current immutable guide snapshot, or an empty optional before the guide service is ready
     */
    public static Optional<GuideSnapshot> activeGuide() {
        return GuideSnapshotService.current();
    }

    /**
     * Projects a possibly absent settings document into the stable, read-only API representation.
     *
     * @param settings settings to project; may be {@code null} during startup or shutdown
     * @return an immutable view when the document is initialized and has a terrain, otherwise an empty optional
     */
    public static Optional<MiningWorldView> worldView(@Nullable WorldSettingsDocument settings) {
        if (settings == null || !settings.initialized()) {
            return Optional.empty();
        }
        @Nullable TerrainMode terrain = settings.terrainMode();
        if (terrain == null) {
            return Optional.empty();
        }
        var dimension = DelvefoldWorldgen.levelFor(terrain, settings.identity().terrainVariant())
                .location();
        return Optional.of(new MiningWorldView(
                settings.identity().displayName(),
                dimension,
                terrain.serializedName(),
                settings.identity().terrainVariant().serializedName(),
                settings.activeProfileId(),
                settings.generationEpoch(),
                settings.identity().renewal().enabled()));
    }

    /**
     * Registers a read-only profile from a startup script without requiring a scripting-mod dependency.
     *
     * @param owner stable namespace identifying the integration that owns the registration
     * @param profileId profile identifier within the owner's namespace
     * @param json complete ore-profile JSON to validate and retain
     */
    public static void registerOreProfileJson(String owner, String profileId, String json) {
        EcosystemProfileRegistry.registerScriptJson(owner, profileId, json);
    }

    /**
     * Removes a script-owned ore profile registration without changing the active persisted profile.
     *
     * @param owner owner namespace used when the profile was registered
     * @param profileId registered profile identifier
     * @return {@code true} when a matching registration was removed
     */
    public static boolean unregisterOreProfile(String owner, String profileId) {
        return EcosystemProfileRegistry.unregisterScriptProfile(owner, profileId);
    }
}
