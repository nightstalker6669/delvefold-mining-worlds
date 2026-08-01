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
        if (settings.generationSalt() < 0) {
            issues.add(ConfigIssue.error("settings.generation_salt.negative", "$.generation_salt",
                    "Generation salt cannot be negative"));
        }
        if (!settings.initialized() && settings.generationSalt() != 0L) {
            issues.add(ConfigIssue.error("settings.generation_salt.uninitialized", "$.generation_salt",
                    "An uninitialized mining world cannot retain an active generation salt"));
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
        if (settings.activeProfileId().length() > 128
                || !settings.activeProfileId().matches("(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+")) {
            issues.add(ConfigIssue.error("settings.profile.invalid", "$.active_profile_id",
                    "Active profile ID must be a lowercase path and may include a namespace"));
        }
        var identity = settings.identity();
        if (identity.displayName().isBlank() || identity.displayName().length() > 64) {
            issues.add(ConfigIssue.error("settings.identity.name", "$.identity.display_name",
                    "World display name must contain 1-64 characters"));
        }
        var renewal = identity.renewal();
        if (renewal.intervalDays() < 1 || renewal.intervalDays() > 3650) {
            issues.add(ConfigIssue.error("settings.renewal.interval", "$.identity.renewal.interval_days",
                    "Renewal interval must be 1-3650 days"));
        }
        if (renewal.warningMinutes() < 1 || renewal.warningMinutes() > 10080) {
            issues.add(ConfigIssue.error("settings.renewal.warning", "$.identity.renewal.warning_minutes",
                    "Renewal warning must be 1-10080 minutes"));
        }
        if (renewal.nextRenewalAtEpochMillis() < 0) {
            issues.add(ConfigIssue.error("settings.renewal.time", "$.identity.renewal.next_renewal_at_epoch_millis",
                    "Next renewal time cannot be negative"));
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
        var hub = settings.portal().hub();
        if (Math.abs((long) hub.x()) > 29_999_936L || Math.abs((long) hub.z()) > 29_999_936L) {
            issues.add(ConfigIssue.error("settings.portal.hub_coordinates", "$.portal.hub",
                    "Central-hub coordinates must remain inside the safe world boundary"));
        }
        if (hub.protectionRadius() < 8 || hub.protectionRadius() > 256) {
            issues.add(ConfigIssue.error("settings.portal.hub_radius", "$.portal.hub.protection_radius",
                    "Central-hub protection radius must be between 8 and 256 blocks"));
        }
        var retention = settings.backupRetention();
        if (retention.maxCount() < 0) {
            issues.add(ConfigIssue.error("settings.backup_retention.count", "$.backup_retention.max_count",
                    "Backup retention count cannot be negative"));
        }
        if (retention.maxAgeDays() < 0) {
            issues.add(ConfigIssue.error("settings.backup_retention.age", "$.backup_retention.max_age_days",
                    "Backup retention age cannot be negative"));
        }
        if (retention.maxTotalBytes() < 0) {
            issues.add(ConfigIssue.error("settings.backup_retention.bytes", "$.backup_retention.max_total_bytes",
                    "Backup retention byte limit cannot be negative"));
        }
        return new ValidationReport(issues);
    }
}
