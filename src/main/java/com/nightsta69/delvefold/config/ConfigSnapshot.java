package com.nightsta69.delvefold.config;

import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.WorldSettingsDocument;
import com.nightsta69.delvefold.config.validation.ValidationReport;
import java.time.Instant;

/**
 * Immutable publication snapshot joining the two canonical configuration documents and their validation state.
 *
 * <p>All referenced model/report values are immutable snapshots. {@code loadedAt} describes this in-memory publication,
 * not a filesystem modification time. The disk hash supports diagnostics and change comparison; it is not a secret,
 * authorization credential, or lifecycle confirmation token.
 *
 * @param ores immutable active ore-profile document
 * @param settings immutable world-settings document
 * @param validation immutable combined ore, settings, and cross-file consistency report
 * @param loadedAt wall-clock instant at which this snapshot was loaded or committed
 * @param diskHash canonical content hash, or a stable built-in-default marker when no accepted disk candidate exists
 */
public record ConfigSnapshot(
        OreProfileDocument ores,
        WorldSettingsDocument settings,
        ValidationReport validation,
        Instant loadedAt,
        String diskHash) {}
