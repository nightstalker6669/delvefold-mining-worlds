package com.nightsta69.delvefold.config;

import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.WorldSettingsDocument;
import com.nightsta69.delvefold.config.validation.ValidationReport;
import java.time.Instant;

public record ConfigSnapshot(
        OreProfileDocument ores,
        WorldSettingsDocument settings,
        ValidationReport validation,
        Instant loadedAt,
        String diskHash
) {
}
