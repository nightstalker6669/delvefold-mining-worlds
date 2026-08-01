package com.nightsta69.delvefold.network.codec;

import com.nightsta69.delvefold.config.analysis.OreProfileForecast;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast.HeightSample;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast.IssueKind;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast.IssueSeverity;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast.ReferenceIssue;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast.ReferenceSummary;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast.RuleForecast;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast.RuleStatus;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast.TerrainTotals;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.network.ProtocolLimits;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** Strict bounded codec for the administrative ore-profile forecast. */
public final class OreForecastStreamCodecs {
    public static final int MAX_NETWORK_BYTES = 24 * 1024;
    public static final int MAX_HEIGHT_SAMPLES = 385;
    public static final int MAX_RULE_ISSUES = 16;
    public static final int MAX_REFERENCE_DETAILS = 64;

    private OreForecastStreamCodecs() {}

    public static void write(RegistryFriendlyByteBuf buffer, OreProfileForecast forecast) {
        int start = buffer.writerIndex();
        buffer.writeVarInt(forecast.formatVersion());
        writeString(buffer, forecast.profileId());
        buffer.writeVarLong(forecast.profileRevision());
        buffer.writeBoolean(forecast.activeTerrain() != null);
        if (forecast.activeTerrain() != null) {
            writeEnum(buffer, forecast.activeTerrain());
        }
        writeCount(buffer, forecast.terrainTotals().size(), TerrainMode.values().length, "terrain totals");
        for (TerrainTotals totals : forecast.terrainTotals()) {
            writeEnum(buffer, totals.terrain());
            buffer.writeBoolean(totals.active());
            writeMetric(buffer, totals.configuredAttempts());
            writeMetric(buffer, totals.configuredWorkUnits());
            writeMetric(buffer, totals.effectiveAttempts());
            writeMetric(buffer, totals.effectiveWorkUnits());
        }
        writeCount(buffer, forecast.activeTerrainHeightOverlay().size(), MAX_HEIGHT_SAMPLES, "height samples");
        for (HeightSample sample : forecast.activeTerrainHeightOverlay()) {
            buffer.writeInt(sample.y());
            writeMetric(buffer, sample.expectedAttempts());
            writeMetric(buffer, sample.expectedWorkUnits());
        }
        buffer.writeVarInt(forecast.totalRuleCount());
        buffer.writeVarInt(forecast.page());
        buffer.writeVarInt(forecast.pageSize());
        buffer.writeVarInt(forecast.pageCount());
        writeCount(buffer, forecast.rules().size(), OreProfileForecast.MAX_RULES_PER_PAGE, "forecast rules");
        for (RuleForecast rule : forecast.rules()) {
            writeRule(buffer, rule);
        }
        writeReferenceSummary(buffer, forecast.references());
        buffer.writeBoolean(forecast.truncated());
        ensureBudget(buffer.writerIndex() - start);
    }

    public static OreProfileForecast read(RegistryFriendlyByteBuf buffer) {
        int start = buffer.readerIndex();
        int format = buffer.readVarInt();
        String profileId = readString(buffer);
        long revision = buffer.readVarLong();
        if (revision < 0L) {
            throw new IllegalArgumentException("Negative forecast revision");
        }
        TerrainMode activeTerrain = buffer.readBoolean() ? readEnum(buffer, TerrainMode.class) : null;
        int terrainCount = readCount(buffer, TerrainMode.values().length, "terrain totals");
        List<TerrainTotals> terrainTotals = new ArrayList<>(terrainCount);
        for (int index = 0; index < terrainCount; index++) {
            terrainTotals.add(new TerrainTotals(
                    readEnum(buffer, TerrainMode.class),
                    buffer.readBoolean(),
                    readMetric(buffer),
                    readMetric(buffer),
                    readMetric(buffer),
                    readMetric(buffer)));
            ensureBudget(buffer.readerIndex() - start);
        }
        int heightCount = readCount(buffer, MAX_HEIGHT_SAMPLES, "height samples");
        List<HeightSample> overlay = new ArrayList<>(heightCount);
        for (int index = 0; index < heightCount; index++) {
            overlay.add(new HeightSample(buffer.readInt(), readMetric(buffer), readMetric(buffer)));
            ensureBudget(buffer.readerIndex() - start);
        }
        int totalRules = boundedInt(buffer.readVarInt(), 0, ProtocolLimits.MAX_ORE_RULES, "total rules");
        int page = boundedInt(buffer.readVarInt(), 0, ProtocolLimits.MAX_ORE_RULES, "page");
        int pageSize = boundedInt(buffer.readVarInt(), 1, OreProfileForecast.MAX_RULES_PER_PAGE, "page size");
        int pageCount = boundedInt(buffer.readVarInt(), 1, ProtocolLimits.MAX_ORE_RULES, "page count");
        int ruleCount = readCount(buffer, OreProfileForecast.MAX_RULES_PER_PAGE, "forecast rules");
        List<RuleForecast> rules = new ArrayList<>(ruleCount);
        for (int index = 0; index < ruleCount; index++) {
            rules.add(readRule(buffer, start));
        }
        ReferenceSummary references = readReferenceSummary(buffer, start);
        boolean truncated = buffer.readBoolean();
        ensureBudget(buffer.readerIndex() - start);
        return new OreProfileForecast(
                format,
                profileId,
                revision,
                activeTerrain,
                terrainTotals,
                overlay,
                totalRules,
                page,
                pageSize,
                pageCount,
                rules,
                references,
                truncated);
    }

    private static void writeRule(RegistryFriendlyByteBuf buffer, RuleForecast rule) {
        buffer.writeVarInt(rule.ruleIndex());
        writeString(buffer, rule.ruleId());
        buffer.writeBoolean(rule.enabled());
        buffer.writeBoolean(rule.required());
        writeEnum(buffer, rule.status());
        writeMetric(buffer, rule.configuredAttempts());
        writeMetric(buffer, rule.configuredWorkUnits());
        writeMetric(buffer, rule.effectiveAttempts());
        writeMetric(buffer, rule.effectiveWorkUnits());
        buffer.writeVarInt(rule.targetCount());
        buffer.writeVarInt(rule.effectiveOutputCount());
        buffer.writeVarInt(rule.missingReferenceCount());
        buffer.writeVarInt(rule.shadowedOutputCount());
        writeCount(buffer, rule.issues().size(), MAX_RULE_ISSUES, "rule issues");
        for (ReferenceIssue issue : rule.issues()) {
            writeIssue(buffer, issue);
        }
        buffer.writeBoolean(rule.truncated());
    }

    private static RuleForecast readRule(RegistryFriendlyByteBuf buffer, int start) {
        int ruleIndex = boundedInt(buffer.readVarInt(), 0, ProtocolLimits.MAX_ORE_RULES - 1, "rule index");
        String ruleId = readString(buffer);
        boolean enabled = buffer.readBoolean();
        boolean required = buffer.readBoolean();
        RuleStatus status = readEnum(buffer, RuleStatus.class);
        double configuredAttempts = readMetric(buffer);
        double configuredWork = readMetric(buffer);
        double effectiveAttempts = readMetric(buffer);
        double effectiveWork = readMetric(buffer);
        int targetCount = boundedInt(buffer.readVarInt(), 0, ProtocolLimits.MAX_VARIANTS, "target count");
        int effectiveOutputs = boundedInt(buffer.readVarInt(), 0, 4096, "effective outputs");
        int missing = boundedInt(buffer.readVarInt(), 0, 4096, "missing references");
        int shadowed = boundedInt(buffer.readVarInt(), 0, 4096, "shadowed outputs");
        int issueCount = readCount(buffer, MAX_RULE_ISSUES, "rule issues");
        List<ReferenceIssue> issues = new ArrayList<>(issueCount);
        for (int index = 0; index < issueCount; index++) {
            issues.add(readIssue(buffer));
        }
        boolean truncated = buffer.readBoolean();
        ensureBudget(buffer.readerIndex() - start);
        return new RuleForecast(
                ruleIndex,
                ruleId,
                enabled,
                required,
                status,
                configuredAttempts,
                configuredWork,
                effectiveAttempts,
                effectiveWork,
                targetCount,
                effectiveOutputs,
                missing,
                shadowed,
                issues,
                truncated);
    }

    private static void writeReferenceSummary(RegistryFriendlyByteBuf buffer, ReferenceSummary summary) {
        buffer.writeVarInt(summary.missingBlocks());
        buffer.writeVarInt(summary.missingOutputTags());
        buffer.writeVarInt(summary.missingHostTags());
        buffer.writeVarInt(summary.invalidStates());
        buffer.writeVarInt(summary.shadowedOutputs());
        buffer.writeVarInt(summary.totalIssues());
        writeCount(buffer, summary.details().size(), MAX_REFERENCE_DETAILS, "reference details");
        for (ReferenceIssue issue : summary.details()) {
            writeIssue(buffer, issue);
        }
        buffer.writeBoolean(summary.truncated());
    }

    private static ReferenceSummary readReferenceSummary(RegistryFriendlyByteBuf buffer, int start) {
        int missingBlocks = countValue(buffer, "missing blocks");
        int missingOutputTags = countValue(buffer, "missing output tags");
        int missingHostTags = countValue(buffer, "missing host tags");
        int invalidStates = countValue(buffer, "invalid states");
        int shadowedOutputs = countValue(buffer, "shadowed outputs");
        int totalIssues = countValue(buffer, "total issues");
        int count = readCount(buffer, MAX_REFERENCE_DETAILS, "reference details");
        List<ReferenceIssue> details = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            details.add(readIssue(buffer));
            ensureBudget(buffer.readerIndex() - start);
        }
        boolean truncated = buffer.readBoolean();
        return new ReferenceSummary(
                missingBlocks,
                missingOutputTags,
                missingHostTags,
                invalidStates,
                shadowedOutputs,
                totalIssues,
                details,
                truncated);
    }

    private static void writeIssue(RegistryFriendlyByteBuf buffer, ReferenceIssue issue) {
        writeEnum(buffer, issue.kind());
        writeEnum(buffer, issue.severity());
        buffer.writeVarInt(issue.ruleIndex());
        buffer.writeInt(issue.targetIndex());
        writeString(buffer, issue.ruleId());
        writeString(buffer, issue.sourceId());
        writeString(buffer, issue.referenceId());
        buffer.writeVarInt(issue.affectedOutputs());
    }

    private static ReferenceIssue readIssue(RegistryFriendlyByteBuf buffer) {
        IssueKind kind = readEnum(buffer, IssueKind.class);
        IssueSeverity severity = readEnum(buffer, IssueSeverity.class);
        int ruleIndex = boundedInt(buffer.readVarInt(), 0, ProtocolLimits.MAX_ORE_RULES - 1, "issue rule index");
        int targetIndex = boundedInt(buffer.readInt(), -1, ProtocolLimits.MAX_VARIANTS - 1, "issue target index");
        return new ReferenceIssue(
                kind,
                severity,
                ruleIndex,
                targetIndex,
                readString(buffer),
                readString(buffer),
                readString(buffer),
                boundedInt(buffer.readVarInt(), 0, 4096, "affected outputs"));
    }

    private static void writeMetric(RegistryFriendlyByteBuf buffer, double value) {
        if (!Double.isFinite(value) || value < 0.0D) {
            throw new IllegalArgumentException("Forecast metrics must be finite and non-negative");
        }
        buffer.writeDouble(value);
    }

    private static double readMetric(RegistryFriendlyByteBuf buffer) {
        double value = buffer.readDouble();
        if (!Double.isFinite(value) || value < 0.0D) {
            throw new IllegalArgumentException("Invalid forecast metric");
        }
        return value;
    }

    private static void writeString(RegistryFriendlyByteBuf buffer, String value) {
        String safe = value == null ? "" : value;
        if (safe.codePointCount(0, safe.length()) > ProtocolLimits.ID_LENGTH) {
            throw new IllegalArgumentException("Forecast identifier exceeds protocol limit");
        }
        buffer.writeUtf(safe, ProtocolLimits.ID_LENGTH);
    }

    private static String readString(RegistryFriendlyByteBuf buffer) {
        return buffer.readUtf(ProtocolLimits.ID_LENGTH);
    }

    private static void writeCount(RegistryFriendlyByteBuf buffer, int count, int maximum, String label) {
        boundedInt(count, 0, maximum, label);
        buffer.writeVarInt(count);
    }

    private static int readCount(RegistryFriendlyByteBuf buffer, int maximum, String label) {
        return boundedInt(buffer.readVarInt(), 0, maximum, label);
    }

    private static int countValue(RegistryFriendlyByteBuf buffer, String label) {
        return boundedInt(buffer.readVarInt(), 0, 1_000_000, label);
    }

    private static int boundedInt(int value, int minimum, int maximum, String label) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("Invalid " + label + ": " + value);
        }
        return value;
    }

    private static <E extends Enum<E>> void writeEnum(RegistryFriendlyByteBuf buffer, E value) {
        buffer.writeVarInt(value.ordinal());
    }

    private static <E extends Enum<E>> E readEnum(RegistryFriendlyByteBuf buffer, Class<E> type) {
        E[] values = type.getEnumConstants();
        int ordinal = buffer.readVarInt();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("Invalid " + type.getSimpleName() + " ordinal: " + ordinal);
        }
        return values[ordinal];
    }

    private static void ensureBudget(int bytes) {
        if (bytes < 0 || bytes > MAX_NETWORK_BYTES) {
            throw new IllegalArgumentException("Forecast payload exceeds its network budget");
        }
    }
}
