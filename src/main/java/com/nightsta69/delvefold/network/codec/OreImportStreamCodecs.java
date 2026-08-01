package com.nightsta69.delvefold.network.codec;

import com.nightsta69.delvefold.config.importer.OreImportModels.DiffStatus;
import com.nightsta69.delvefold.config.importer.OreImportModels.Evidence;
import com.nightsta69.delvefold.config.importer.OreImportModels.HostKind;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.validation.IssueSeverity;
import com.nightsta69.delvefold.network.ProtocolLimits;
import com.nightsta69.delvefold.network.model.OreImportViews.CandidateView;
import com.nightsta69.delvefold.network.model.OreImportViews.DiffView;
import com.nightsta69.delvefold.network.model.OreImportViews.GroupView;
import com.nightsta69.delvefold.network.model.OreImportViews.PreviewView;
import com.nightsta69.delvefold.network.model.OreImportViews.ScanView;
import com.nightsta69.delvefold.network.model.OreImportViews.TerrainDeltaView;
import com.nightsta69.delvefold.network.model.OreImportViews.ValidationIssueView;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** Strict bounded codecs for ore-import scan and preview pages. */
public final class OreImportStreamCodecs {
    private OreImportStreamCodecs() {
    }

    public static void writeScan(RegistryFriendlyByteBuf buffer, ScanView view) {
        int start = buffer.writerIndex();
        writeToken(buffer, view.scanToken());
        buffer.writeVarLong(view.expectedOreRevision());
        writeId(buffer, view.baseProfileId());
        buffer.writeVarInt(view.page());
        buffer.writeVarInt(view.pageCount());
        buffer.writeVarInt(view.totalGroups());
        buffer.writeVarInt(view.scannedBlocks());
        buffer.writeBoolean(view.truncated());
        writeCount(buffer, view.groups().size(), ProtocolLimits.MAX_IMPORT_GROUPS_PER_PAGE, "import groups");
        for (GroupView group : view.groups()) {
            writeId(buffer, group.id());
            writeId(buffer, group.namespace());
            writeId(buffer, group.material());
            writeEnum(buffer, group.evidence());
            buffer.writeBoolean(group.reviewRequired());
            writeCount(buffer, group.candidates().size(), ProtocolLimits.MAX_VARIANTS, "import candidates");
            for (CandidateView candidate : group.candidates()) {
                writeId(buffer, candidate.blockId());
                writeOptionalId(buffer, candidate.replaceTag());
                writeEnum(buffer, candidate.hostKind());
                writeEnum(buffer, candidate.evidence());
            }
        }
        ensureBudget(buffer.writerIndex() - start);
    }

    public static ScanView readScan(RegistryFriendlyByteBuf buffer) {
        int start = buffer.readerIndex();
        String token = readToken(buffer);
        long revision = nonNegative(buffer.readVarLong(), "ore revision");
        String base = readId(buffer);
        int page = bounded(buffer.readVarInt(), 0, ProtocolLimits.MAX_IMPORT_GROUPS, "scan page");
        int pageCount = bounded(buffer.readVarInt(), 1, ProtocolLimits.MAX_IMPORT_GROUPS, "scan page count");
        int total = bounded(buffer.readVarInt(), 0, ProtocolLimits.MAX_IMPORT_GROUPS, "total groups");
        int scanned = bounded(buffer.readVarInt(), 0, 1_000_000, "scanned blocks");
        boolean truncated = buffer.readBoolean();
        int count = readCount(buffer, ProtocolLimits.MAX_IMPORT_GROUPS_PER_PAGE, "import groups");
        List<GroupView> groups = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            String id = readId(buffer);
            String namespace = readId(buffer);
            String material = readId(buffer);
            Evidence evidence = readEnum(buffer, Evidence.class);
            boolean review = buffer.readBoolean();
            int candidates = readCount(buffer, ProtocolLimits.MAX_VARIANTS, "import candidates");
            List<CandidateView> candidateViews = new ArrayList<>(candidates);
            for (int candidate = 0; candidate < candidates; candidate++) {
                candidateViews.add(new CandidateView(
                        readId(buffer), readOptionalId(buffer),
                        readEnum(buffer, HostKind.class), readEnum(buffer, Evidence.class)));
            }
            groups.add(new GroupView(id, namespace, material, evidence, review, candidateViews));
            ensureBudget(buffer.readerIndex() - start);
        }
        return new ScanView(token, revision, base, page, pageCount, total, scanned, truncated, groups);
    }

    public static void writePreview(RegistryFriendlyByteBuf buffer, PreviewView view) {
        int start = buffer.writerIndex();
        writeToken(buffer, view.commitToken());
        buffer.writeVarLong(view.expectedOreRevision());
        writeId(buffer, view.baseProfileId());
        buffer.writeVarInt(view.page());
        buffer.writeVarInt(view.pageCount());
        buffer.writeVarInt(view.totalDiffEntries());
        buffer.writeBoolean(view.valid());
        buffer.writeVarLong(view.addedRuleCount());
        writeCount(buffer, view.diff().size(), ProtocolLimits.MAX_IMPORT_DIFF_PER_PAGE, "import diff entries");
        for (DiffView entry : view.diff()) {
            writeId(buffer, entry.groupId());
            writeEnum(buffer, entry.status());
            writeOptionalId(buffer, entry.ruleId());
            writeIds(buffer, entry.addedBlocks(), ProtocolLimits.MAX_VARIANTS, "added blocks");
            writeIds(buffer, entry.skippedBlocks(), ProtocolLimits.MAX_VARIANTS, "skipped blocks");
            writeText(buffer, entry.message(), ProtocolLimits.MAX_IMPORT_MESSAGE_LENGTH);
        }
        writeCount(buffer, view.workloads().size(), TerrainMode.values().length, "terrain workloads");
        for (TerrainDeltaView workload : view.workloads()) {
            writeEnum(buffer, workload.terrain());
            writeMetric(buffer, workload.beforeAttempts());
            writeMetric(buffer, workload.beforeWorkUnits());
            writeMetric(buffer, workload.afterAttempts());
            writeMetric(buffer, workload.afterWorkUnits());
        }
        writeCount(buffer, view.issues().size(), ProtocolLimits.MAX_IMPORT_ISSUES, "validation issues");
        for (ValidationIssueView issue : view.issues()) {
            writeEnum(buffer, issue.severity());
            writeId(buffer, issue.code());
            writeText(buffer, issue.path(), ProtocolLimits.SHORT_TEXT_LENGTH);
            writeText(buffer, issue.message(), ProtocolLimits.MAX_IMPORT_MESSAGE_LENGTH);
        }
        buffer.writeBoolean(view.truncated());
        ensureBudget(buffer.writerIndex() - start);
    }

    public static PreviewView readPreview(RegistryFriendlyByteBuf buffer) {
        int start = buffer.readerIndex();
        String token = readToken(buffer);
        long revision = nonNegative(buffer.readVarLong(), "ore revision");
        String base = readId(buffer);
        int page = bounded(buffer.readVarInt(), 0, ProtocolLimits.MAX_IMPORT_GROUPS, "preview page");
        int pageCount = bounded(buffer.readVarInt(), 1, ProtocolLimits.MAX_IMPORT_GROUPS, "preview page count");
        int total = bounded(buffer.readVarInt(), 0, ProtocolLimits.MAX_IMPORT_SELECTED_GROUPS, "diff count");
        boolean valid = buffer.readBoolean();
        long addedRules = nonNegative(buffer.readVarLong(), "added rules");
        int diffCount = readCount(buffer, ProtocolLimits.MAX_IMPORT_DIFF_PER_PAGE, "import diff entries");
        List<DiffView> diff = new ArrayList<>(diffCount);
        for (int index = 0; index < diffCount; index++) {
            String groupId = readId(buffer);
            DiffStatus status = readEnum(buffer, DiffStatus.class);
            String ruleId = readOptionalId(buffer);
            List<String> added = readIds(buffer, ProtocolLimits.MAX_VARIANTS, "added blocks");
            List<String> skipped = readIds(buffer, ProtocolLimits.MAX_VARIANTS, "skipped blocks");
            diff.add(new DiffView(groupId, status, ruleId, added, skipped,
                    readText(buffer, ProtocolLimits.MAX_IMPORT_MESSAGE_LENGTH)));
            ensureBudget(buffer.readerIndex() - start);
        }
        int workloadCount = readCount(buffer, TerrainMode.values().length, "terrain workloads");
        List<TerrainDeltaView> workloads = new ArrayList<>(workloadCount);
        for (int index = 0; index < workloadCount; index++) {
            workloads.add(new TerrainDeltaView(readEnum(buffer, TerrainMode.class),
                    readMetric(buffer), readMetric(buffer), readMetric(buffer), readMetric(buffer)));
        }
        int issueCount = readCount(buffer, ProtocolLimits.MAX_IMPORT_ISSUES, "validation issues");
        List<ValidationIssueView> issues = new ArrayList<>(issueCount);
        for (int index = 0; index < issueCount; index++) {
            issues.add(new ValidationIssueView(
                    readEnum(buffer, IssueSeverity.class), readId(buffer),
                    readText(buffer, ProtocolLimits.SHORT_TEXT_LENGTH),
                    readText(buffer, ProtocolLimits.MAX_IMPORT_MESSAGE_LENGTH)));
            ensureBudget(buffer.readerIndex() - start);
        }
        boolean truncated = buffer.readBoolean();
        return new PreviewView(token, revision, base, page, pageCount, total, valid,
                addedRules, diff, workloads, issues, truncated);
    }

    private static void writeIds(RegistryFriendlyByteBuf buffer, List<String> values, int maximum, String label) {
        writeCount(buffer, values.size(), maximum, label);
        for (String value : values) {
            writeId(buffer, value);
        }
    }

    private static List<String> readIds(RegistryFriendlyByteBuf buffer, int maximum, String label) {
        int count = readCount(buffer, maximum, label);
        List<String> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            values.add(readId(buffer));
        }
        return values;
    }

    private static void writeToken(RegistryFriendlyByteBuf buffer, String value) {
        writeText(buffer, value, ProtocolLimits.MAX_IMPORT_TOKEN_LENGTH);
    }

    private static String readToken(RegistryFriendlyByteBuf buffer) {
        String value = readText(buffer, ProtocolLimits.MAX_IMPORT_TOKEN_LENGTH);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Import token cannot be blank");
        }
        return value;
    }

    private static void writeId(RegistryFriendlyByteBuf buffer, String value) {
        writeText(buffer, value, ProtocolLimits.ID_LENGTH);
    }

    private static String readId(RegistryFriendlyByteBuf buffer) {
        String value = readText(buffer, ProtocolLimits.ID_LENGTH);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Import identifier cannot be blank");
        }
        return value;
    }

    private static void writeOptionalId(RegistryFriendlyByteBuf buffer, String value) {
        writeText(buffer, value == null ? "" : value, ProtocolLimits.ID_LENGTH);
    }

    private static String readOptionalId(RegistryFriendlyByteBuf buffer) {
        return readText(buffer, ProtocolLimits.ID_LENGTH);
    }

    private static void writeText(RegistryFriendlyByteBuf buffer, String value, int maximum) {
        String safe = value == null ? "" : value;
        if (safe.codePointCount(0, safe.length()) > maximum) {
            throw new IllegalArgumentException("Import text exceeds protocol limit");
        }
        buffer.writeUtf(safe, maximum);
    }

    private static String readText(RegistryFriendlyByteBuf buffer, int maximum) {
        return buffer.readUtf(maximum);
    }

    private static void writeCount(RegistryFriendlyByteBuf buffer, int count, int maximum, String label) {
        bounded(count, 0, maximum, label);
        buffer.writeVarInt(count);
    }

    private static int readCount(RegistryFriendlyByteBuf buffer, int maximum, String label) {
        return bounded(buffer.readVarInt(), 0, maximum, label);
    }

    private static void writeMetric(RegistryFriendlyByteBuf buffer, double value) {
        if (!Double.isFinite(value) || value < 0.0D) {
            throw new IllegalArgumentException("Invalid import workload metric");
        }
        buffer.writeDouble(value);
    }

    private static double readMetric(RegistryFriendlyByteBuf buffer) {
        double value = buffer.readDouble();
        if (!Double.isFinite(value) || value < 0.0D) {
            throw new IllegalArgumentException("Invalid import workload metric");
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

    private static int bounded(int value, int minimum, int maximum, String label) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("Invalid " + label + ": " + value);
        }
        return value;
    }

    private static long nonNegative(long value, String label) {
        if (value < 0L) {
            throw new IllegalArgumentException("Invalid " + label + ": " + value);
        }
        return value;
    }

    private static void ensureBudget(int bytes) {
        if (bytes < 0 || bytes > ProtocolLimits.MAX_IMPORT_NETWORK_BYTES) {
            throw new IllegalArgumentException("Ore-import payload exceeds its network budget");
        }
    }
}
