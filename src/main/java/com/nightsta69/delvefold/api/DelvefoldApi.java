package com.nightsta69.delvefold.api;

import com.nightsta69.delvefold.config.DelvefoldConfigService;
import com.nightsta69.delvefold.config.EcosystemProfileRegistry;
import com.nightsta69.delvefold.config.model.WorldSettingsDocument;
import com.nightsta69.delvefold.world.DelvefoldWorldgen;
import java.util.Optional;

/** Stable Delvefold 1.x integration surface. New 1.x APIs will remain backward compatible. */
public final class DelvefoldApi {
    public static final int API_VERSION = 1;

    private DelvefoldApi() {
    }

    public static Optional<MiningWorldView> activeWorld() {
        try {
            return worldView(DelvefoldConfigService.get().snapshot().settings());
        } catch (IllegalStateException ignored) {
            return Optional.empty();
        }
    }

    public static Optional<MiningWorldView> worldView(WorldSettingsDocument settings) {
        if (settings == null || !settings.initialized() || settings.terrainMode() == null) {
            return Optional.empty();
        }
        var dimension = DelvefoldWorldgen.levelFor(
                settings.terrainMode(), settings.identity().terrainVariant()).location();
        return Optional.of(new MiningWorldView(
                settings.identity().displayName(),
                dimension,
                settings.terrainMode().serializedName(),
                settings.identity().terrainVariant().serializedName(),
                settings.activeProfileId(),
                settings.generationEpoch(),
                settings.identity().renewal().enabled()));
    }

    /** Registers a read-only profile from a startup script without requiring a scripting-mod dependency. */
    public static void registerOreProfileJson(String owner, String profileId, String json) {
        EcosystemProfileRegistry.registerScriptJson(owner, profileId, json);
    }

    public static boolean unregisterOreProfile(String owner, String profileId) {
        return EcosystemProfileRegistry.unregisterScriptProfile(owner, profileId);
    }
}
