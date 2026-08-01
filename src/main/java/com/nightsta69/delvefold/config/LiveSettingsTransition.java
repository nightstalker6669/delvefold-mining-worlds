package com.nightsta69.delvefold.config;

import com.nightsta69.delvefold.config.model.WorldSettingsDocument;
import com.nightsta69.delvefold.config.validation.ConfigIssue;
import java.util.ArrayList;
import java.util.List;

/** Guards lifecycle-owned settings when canonical JSON is reloaded on a running server. */
final class LiveSettingsTransition {
    private LiveSettingsTransition() {
    }

    static List<ConfigIssue> validate(WorldSettingsDocument active, WorldSettingsDocument candidate) {
        if (active == null || candidate == null) {
            return List.of();
        }
        List<ConfigIssue> issues = new ArrayList<>();
        locked(issues, active.generationEpoch() == candidate.generationEpoch(),
                "settings.lifecycle.epoch_locked", "$.generation_epoch", "generation_epoch");
        locked(issues, active.generationSalt() == candidate.generationSalt(),
                "settings.lifecycle.salt_locked", "$.generation_salt", "generation_salt");
        locked(issues, active.initialized() == candidate.initialized(),
                "settings.lifecycle.initialized_locked", "$.initialized", "initialized");
        locked(issues, active.terrainMode() == candidate.terrainMode(),
                "settings.lifecycle.terrain_locked", "$.terrain_mode", "terrain_mode");
        locked(issues, active.identity().terrainVariant() == candidate.identity().terrainVariant(),
                "settings.lifecycle.variant_locked", "$.identity.terrain_variant", "terrain_variant");
        locked(issues, active.lastWorldOperationId().equals(candidate.lastWorldOperationId()),
                "settings.lifecycle.operation_locked", "$.last_world_operation_id", "last_world_operation_id");
        return List.copyOf(issues);
    }

    private static void locked(
            List<ConfigIssue> issues, boolean unchanged, String code, String path, String field) {
        if (!unchanged) {
            issues.add(ConfigIssue.error(code, path,
                    field + " is managed by initialization and confirmed world operations; restart or restore "
                            + "through Delvefold instead of changing it during a live config reload"));
        }
    }
}
