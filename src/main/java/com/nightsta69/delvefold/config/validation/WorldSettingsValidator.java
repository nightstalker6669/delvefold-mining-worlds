package com.nightsta69.delvefold.config.validation;

import com.nightsta69.delvefold.config.model.WorldSettingsDocument;
import java.util.ArrayList;
import java.util.List;

public final class WorldSettingsValidator {
    private WorldSettingsValidator() {
    }

    public static ValidationReport validate(WorldSettingsDocument settings) {
        List<ConfigIssue> issues = new ArrayList<>();
        if (settings == null) {
            return new ValidationReport(List.of(ConfigIssue.error("settings.missing", "$", "Settings document is missing")));
        }
        if (settings.schemaVersion() != WorldSettingsDocument.CURRENT_SCHEMA_VERSION) {
            issues.add(ConfigIssue.error("settings.schema.unsupported", "$.schema_version",
                    "Unsupported settings schema version: " + settings.schemaVersion()));
        }
        if (settings.revision() < 0) {
            issues.add(ConfigIssue.error("settings.revision.negative", "$.revision", "Revision cannot be negative"));
        }
        if (settings.generationEpoch() < 0) {
            issues.add(ConfigIssue.error("settings.epoch.negative", "$.generation_epoch", "Generation epoch cannot be negative"));
        }
        if (settings.initialized() && settings.terrainMode() == null) {
            issues.add(ConfigIssue.error("settings.terrain.missing", "$.terrain_mode", "Initialized worlds require a terrain mode"));
        }
        if (!settings.initialized() && settings.terrainMode() != null) {
            issues.add(ConfigIssue.error("settings.terrain.unexpected", "$.terrain_mode", "Uninitialized worlds cannot select a terrain mode"));
        }
        if (settings.lastWorldOperationId().length() > 128) {
            issues.add(ConfigIssue.error("settings.operation_id.too_long", "$.last_world_operation_id",
                    "The last world operation ID cannot exceed 128 characters"));
        }
        if (!settings.portal().playerOnly()) {
            issues.add(ConfigIssue.error("settings.portal.player_only", "$.portal.player_only",
                    "Delvefold portals are player-only"));
        }
        int cooldown = settings.portal().cooldownSeconds();
        if (cooldown < 1 || cooldown > 3600) {
            issues.add(ConfigIssue.error("settings.portal.invalid_cooldown", "$.portal.cooldown_seconds",
                    "Portal cooldown must be between 1 and 3600 seconds"));
        }
        double scale = settings.portal().coordinateScale();
        if (!Double.isFinite(scale) || scale < 0.01D || scale > 100.0D) {
            issues.add(ConfigIssue.error("settings.portal.invalid_scale", "$.portal.coordinate_scale",
                    "Portal coordinate scale must be finite and between 0.01 and 100"));
        }
        return new ValidationReport(issues);
    }
}
