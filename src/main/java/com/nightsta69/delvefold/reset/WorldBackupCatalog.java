package com.nightsta69.delvefold.reset;

import com.nightsta69.delvefold.config.ConfigJson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Read-only, path-contained view of recoverable Delvefold world backups. */
public final class WorldBackupCatalog {
    private static final long MAX_MARKER_BYTES = 64 * 1024;
    private final Path saveRoot;
    private final Path backupRoot;

    public WorldBackupCatalog(Path saveRoot) {
        this.saveRoot = saveRoot.toAbsolutePath().normalize();
        this.backupRoot = this.saveRoot.resolve("delvefold_backups").normalize();
    }

    public List<BackupSummary> list() throws IOException {
        if (Files.notExists(backupRoot)) {
            return List.of();
        }
        if (Files.isSymbolicLink(backupRoot) || !Files.isDirectory(backupRoot)) {
            throw new IOException("Delvefold backup root failed safety checks");
        }
        List<BackupSummary> result = new ArrayList<>();
        try (Stream<Path> entries = Files.list(backupRoot)) {
            for (Path entry : entries.toList()) {
                if (Files.isSymbolicLink(entry) || !Files.isDirectory(entry)) {
                    continue;
                }
                try {
                    result.add(readSummary(entry));
                } catch (IOException | RuntimeException ignored) {
                    result.add(new BackupSummary(entry.getFileName().toString(), 0, "unknown", "unknown",
                            size(entry), false, false, false));
                }
            }
        }
        return result.stream().sorted(Comparator.comparingLong(BackupSummary::createdAtEpochMillis).reversed()).toList();
    }

    public Path resolve(String id) throws IOException {
        if (id == null || !id.matches("[a-zA-Z0-9_.-]{1,200}")) {
            throw new IOException("Invalid backup ID");
        }
        Path result = backupRoot.resolve(id).normalize();
        if (!result.startsWith(backupRoot) || result.equals(backupRoot)
                || Files.isSymbolicLink(result) || !Files.isDirectory(result)) {
            throw new IOException("Unknown or unsafe backup ID: " + id);
        }
        return result;
    }

    private BackupSummary readSummary(Path root) throws IOException {
        Path marker = root.resolve("operation.json");
        if (Files.isSymbolicLink(marker) || !Files.isRegularFile(marker) || Files.size(marker) > MAX_MARKER_BYTES) {
            throw new IOException("Backup marker failed safety checks");
        }
        PendingWorldOperation operation = ConfigJson.GSON.fromJson(
                Files.readString(marker, StandardCharsets.UTF_8), PendingWorldOperation.class);
        if (operation == null || operation.schemaVersion() != PendingWorldOperation.CURRENT_SCHEMA_VERSION) {
            throw new IOException("Backup marker schema is unsupported");
        }
        boolean hasSettings = Files.isRegularFile(root.resolve("config/serverconfig/delvefold/settings.json"));
        boolean hasOres = Files.isRegularFile(root.resolve("config/serverconfig/delvefold/ores.json"));
        boolean hasDimensions = Files.isDirectory(root.resolve("dimensions/delvefold"));
        return new BackupSummary(root.getFileName().toString(), operation.createdAtEpochMillis(),
                operation.type().name().toLowerCase(java.util.Locale.ROOT),
                operation.sourceTerrain() == null ? "unknown" : operation.sourceTerrain().serializedName(),
                size(root), Files.isRegularFile(root.resolve(".pinned")),
                hasSettings && hasOres && hasDimensions, true);
    }

    private static long size(Path root) {
        long total = 0;
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                try {
                    total += Files.size(path);
                } catch (IOException ignored) {
                    return -1;
                }
            }
        } catch (IOException ignored) {
            return -1;
        }
        return total;
    }

    public record BackupSummary(
            String id,
            long createdAtEpochMillis,
            String operation,
            String terrain,
            long sizeBytes,
            boolean pinned,
            boolean restorable,
            boolean valid) {
        public Instant createdAt() {
            return Instant.ofEpochMilli(createdAtEpochMillis);
        }
    }
}
