package com.nightsta69.delvefold.reset;

import com.nightsta69.delvefold.config.ConfigJson;
import com.nightsta69.delvefold.config.model.BackupRetentionSettings;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Path-based preview/apply boundary for safe automatic backup retention. */
public final class BackupRetentionService {
    private static final long MAX_PENDING_BYTES = 64L * 1024L;
    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter
            .ofPattern("uuuuMMdd-HHmmss", Locale.ROOT)
            .withZone(ZoneOffset.UTC);

    private BackupRetentionService() {
    }

    public static Preview preview(
            Path saveRoot,
            BackupRetentionSettings settings,
            Instant now) throws IOException {
        Path root = checkedSaveRoot(saveRoot);
        BackupRetentionSettings policy = settings == null ? BackupRetentionSettings.defaults() : settings;
        Instant evaluatedAt = now == null ? Instant.now() : now;
        WorldBackupCatalog catalog = new WorldBackupCatalog(root);
        List<BackupRetentionPlanner.Candidate> candidates = catalog.list().stream()
                .map(BackupRetentionPlanner::fromSummary)
                .toList();
        Set<String> protectedIds = protectedBackupIds(root);
        BackupRetentionPlanner.Plan plan = BackupRetentionPlanner.plan(
                policy, candidates, protectedIds, evaluatedAt);
        return new Preview(root, policy, evaluatedAt, protectedIds, plan);
    }

    /**
     * Applies only IDs present in both the audited preview and a fresh safety
     * preview. New pins, pending references, or newest-two protection win.
     */
    public static ApplyResult apply(Preview preview) throws IOException {
        if (preview == null) {
            throw new IOException("Backup retention preview is missing");
        }
        Path root = checkedSaveRoot(preview.saveRoot());
        if (!preview.settings().enabled() || !preview.plan().enabled() || preview.plan().prunes().isEmpty()) {
            return new ApplyResult(List.of(), Map.of());
        }
        List<String> planned = preview.plan().prunes().stream()
                .map(BackupRetentionPlanner.Prune::id)
                .sorted()
                .toList();
        List<String> pruned = new ArrayList<>();
        Map<String, String> failures = new LinkedHashMap<>();
        WorldBackupCatalog catalog = new WorldBackupCatalog(root);
        for (String id : planned) {
            Preview fresh = preview(root, preview.settings(), preview.evaluatedAt());
            Set<String> stillEligible = fresh.plan().prunes().stream()
                    .map(BackupRetentionPlanner.Prune::id)
                    .collect(java.util.stream.Collectors.toSet());
            if (!stillEligible.contains(id)) {
                failures.put(id, "Backup became protected or is no longer eligible after the preview");
                continue;
            }
            try {
                if (catalog.delete(id)) {
                    pruned.add(id);
                } else {
                    failures.put(id, "Backup deletion did not complete");
                }
            } catch (IOException exception) {
                failures.put(id, safeMessage(exception));
            }
        }
        return new ApplyResult(pruned, failures);
    }

    /**
     * Reads persisted operation journals conservatively. If a journal exists but
     * is malformed, preview fails and automatic pruning must be skipped.
     */
    public static Set<String> protectedBackupIds(Path saveRoot) throws IOException {
        Path root = checkedSaveRoot(saveRoot);
        Path configRoot = root.resolve("serverconfig/delvefold").normalize();
        if (!configRoot.startsWith(root)) {
            throw new IOException("Delvefold configuration path escaped the save root");
        }
        ensureNoSymbolicLinkBetween(root, configRoot);
        Set<String> result = new LinkedHashSet<>();

        Path restorePath = configRoot.resolve("pending_restore.json");
        if (Files.exists(restorePath) || Files.isSymbolicLink(restorePath)) {
            PendingWorldRestore restore = readPending(restorePath, PendingWorldRestore.class);
            if (restore.schemaVersion() != PendingWorldRestore.CURRENT_SCHEMA_VERSION) {
                throw new IOException("Pending restore schema is unsupported");
            }
            requireOperationId(restore.operationId());
            requireBackupId(restore.backupId());
            requirePositiveTimestamp(restore.createdAtEpochMillis());
            if (restore.phase() == null) {
                throw new IOException("Pending restore phase is missing");
            }
            result.add(restore.backupId());
            result.add(formatBackupTimestamp(restore.createdAtEpochMillis())
                    + "-pre-restore-" + restore.operationId());
        }

        Path operationPath = configRoot.resolve("pending_world_operation.json");
        if (Files.exists(operationPath) || Files.isSymbolicLink(operationPath)) {
            PendingWorldOperation operation = readPending(operationPath, PendingWorldOperation.class);
            if (operation.schemaVersion() != PendingWorldOperation.CURRENT_SCHEMA_VERSION) {
                throw new IOException("Pending world-operation schema is unsupported");
            }
            requireOperationId(operation.operationId());
            requirePositiveTimestamp(operation.createdAtEpochMillis());
            if (operation.type() == null) {
                throw new IOException("Pending world-operation type is missing");
            }
            if (operation.backupMode() == null) {
                throw new IOException("Pending world-operation backup mode is missing");
            }
            if (operation.type() == WorldOperationType.RECREATE && operation.targetTerrain() == null) {
                throw new IOException("Pending recreation target terrain is missing");
            }
            if (operation.backupMode() == BackupMode.KEEP_BACKUP) {
                result.add(formatBackupTimestamp(operation.createdAtEpochMillis())
                        + '-' + operation.operationId());
            }
        }
        List<String> ordered = result.stream().sorted().toList();
        return Collections.unmodifiableSet(new LinkedHashSet<>(ordered));
    }

    private static Path checkedSaveRoot(Path supplied) throws IOException {
        if (supplied == null) {
            throw new IOException("Save root is missing");
        }
        Path root = supplied.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root)) {
            throw new IOException("Save root failed safety checks");
        }
        return root;
    }

    private static <T> T readPending(Path path, Class<T> type) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path) || Files.size(path) > MAX_PENDING_BYTES) {
            throw new IOException("Pending operation journal failed safety checks");
        }
        try {
            JsonObject object = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            requirePendingFields(object, type);
            T value = ConfigJson.GSON.fromJson(object, type);
            if (value == null) {
                throw new IOException("Pending operation journal is empty");
            }
            return value;
        } catch (RuntimeException exception) {
            throw new IOException("Pending operation journal is invalid", exception);
        }
    }

    private static void requirePendingFields(JsonObject object, Class<?> type) throws IOException {
        if (type == PendingWorldRestore.class) {
            requireJsonFields(object, "schema_version", "operation_id", "backup_id", "phase",
                    "created_at_epoch_millis", "requested_by");
        } else if (type == PendingWorldOperation.class) {
            requireJsonFields(object, "schema_version", "operation_id", "type", "backup_mode",
                    "created_at_epoch_millis", "requested_by");
        }
    }

    private static void requireJsonFields(JsonObject object, String... names) throws IOException {
        for (String name : names) {
            if (!object.has(name) || object.get(name).isJsonNull()) {
                throw new IOException("Pending operation journal is missing required field " + name);
            }
        }
    }

    private static void requireBackupId(String id) throws IOException {
        if (id == null || !id.matches("[a-zA-Z0-9_.-]{1,200}")) {
            throw new IOException("Pending restore references an invalid backup ID");
        }
    }

    private static void requireOperationId(String id) throws IOException {
        try {
            UUID.fromString(id);
        } catch (RuntimeException exception) {
            throw new IOException("Pending operation references an invalid operation ID", exception);
        }
    }

    private static void requirePositiveTimestamp(long timestamp) throws IOException {
        if (timestamp <= 0L) {
            throw new IOException("Pending operation creation time is invalid");
        }
    }

    private static String formatBackupTimestamp(long timestamp) throws IOException {
        requirePositiveTimestamp(timestamp);
        try {
            return BACKUP_TIMESTAMP.format(Instant.ofEpochMilli(timestamp));
        } catch (RuntimeException exception) {
            throw new IOException("Pending operation creation time cannot be represented", exception);
        }
    }

    private static void ensureNoSymbolicLinkBetween(Path root, Path target) throws IOException {
        Path current = root;
        for (Path part : root.relativize(target)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Delvefold configuration path contains a symbolic link");
            }
        }
    }

    private static String safeMessage(IOException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "Backup deletion failed" : message;
    }

    public record Preview(
            Path saveRoot,
            BackupRetentionSettings settings,
            Instant evaluatedAt,
            Set<String> protectedBackupIds,
            BackupRetentionPlanner.Plan plan
    ) {
        public Preview {
            saveRoot = saveRoot.toAbsolutePath().normalize();
            settings = settings == null ? BackupRetentionSettings.defaults() : settings;
            evaluatedAt = evaluatedAt == null ? Instant.EPOCH : evaluatedAt;
            if (protectedBackupIds == null || protectedBackupIds.isEmpty()) {
                protectedBackupIds = Set.of();
            } else {
                protectedBackupIds = Collections.unmodifiableSet(new LinkedHashSet<>(
                        protectedBackupIds.stream().sorted().toList()));
            }
        }
    }

    public record ApplyResult(List<String> prunedIds, Map<String, String> failures) {
        public ApplyResult {
            prunedIds = prunedIds == null ? List.of() : List.copyOf(prunedIds);
            if (failures == null || failures.isEmpty()) {
                failures = Map.of();
            } else {
                List<Map.Entry<String, String>> ordered = failures.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .toList();
                Map<String, String> copy = new LinkedHashMap<>();
                ordered.forEach(entry -> copy.put(entry.getKey(), entry.getValue()));
                failures = Collections.unmodifiableMap(copy);
            }
        }

        public boolean successful() {
            return failures.isEmpty();
        }
    }
}
