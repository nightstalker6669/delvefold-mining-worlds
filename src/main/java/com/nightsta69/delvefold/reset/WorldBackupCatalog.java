package com.nightsta69.delvefold.reset;

import com.nightsta69.delvefold.config.ConfigJson;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.WorldSettingsDocument;
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
    private final BackupManifestService manifests = new BackupManifestService();

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
                    boolean manifestPresent = Files.exists(entry.resolve(BackupManifest.FILE_NAME))
                            || Files.isSymbolicLink(entry.resolve(BackupManifest.FILE_NAME));
                    result.add(new BackupSummary(entry.getFileName().toString(), 0, "unknown", "unknown",
                            -1L, Files.exists(entry.resolve(".pinned")), false, false,
                            manifestPresent, false, !manifestPresent));
                }
            }
        }
        return result.stream().sorted(Comparator.comparingLong(BackupSummary::createdAtEpochMillis).reversed()).toList();
    }

    public Path resolve(String id) throws IOException {
        if (id == null || !id.matches("[a-zA-Z0-9_.-]{1,200}")) {
            throw new IOException("Invalid backup ID");
        }
        if (Files.isSymbolicLink(backupRoot) || !Files.isDirectory(backupRoot)) {
            throw new IOException("Delvefold backup root failed safety checks");
        }
        Path result = backupRoot.resolve(id).normalize();
        if (!result.startsWith(backupRoot) || result.equals(backupRoot)
                || Files.isSymbolicLink(result) || !Files.isDirectory(result)) {
            throw new IOException("Unknown or unsafe backup ID: " + id);
        }
        return result;
    }

    public boolean setPinned(String id, boolean pinned) throws IOException {
        Path root = resolve(id);
        Path marker = root.resolve(".pinned");
        if (Files.isSymbolicLink(marker)) {
            throw new IOException("Backup pin marker failed safety checks");
        }
        if (Files.exists(marker) && !Files.isRegularFile(marker)) {
            throw new IOException("Backup pin marker failed safety checks");
        }
        if (pinned) {
            if (Files.exists(marker)) {
                return false;
            } else {
                Files.writeString(marker, "pinned\n", StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE_NEW,
                        java.nio.file.StandardOpenOption.WRITE);
            }
            return true;
        }
        return Files.deleteIfExists(marker);
    }

    public boolean delete(String id) throws IOException {
        Path root = resolve(id);
        if (Files.exists(root.resolve(".pinned"))) {
            throw new IOException("Pinned backups must be unpinned before deletion");
        }
        try (Stream<Path> paths = Files.walk(root)) {
            List<Path> deletionOrder = paths.sorted(Comparator.reverseOrder()).toList();
            for (Path path : deletionOrder) {
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("Backup contains a symbolic link and was not deleted");
                }
            }
            for (Path path : deletionOrder) {
                Files.delete(path);
            }
        }
        return Files.notExists(root);
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
        WorldSettingsDocument settings = BackupManifestService.readCurrentSchema(
                root.resolve("config/serverconfig/delvefold/settings.json"),
                WorldSettingsDocument.class, WorldSettingsDocument.CURRENT_SCHEMA_VERSION);
        OreProfileDocument ores = BackupManifestService.readCurrentSchema(
                root.resolve("config/serverconfig/delvefold/ores.json"),
                OreProfileDocument.class, OreProfileDocument.CURRENT_SCHEMA_VERSION);
        boolean hasDimensions = false;
        if (settings != null) {
            try {
                BackupManifestService.validateDimensionSnapshot(root, operation, settings);
                hasDimensions = true;
            } catch (IOException ignored) {
                // Invalid legacy layouts stay listed, but cannot become restorable.
            }
        }
        boolean baseRestorable = settings != null && ores != null && hasDimensions;
        boolean manifestPresent = Files.exists(root.resolve(BackupManifest.FILE_NAME));
        boolean manifestValid = false;
        boolean verified = false;
        long manifestTotalBytes = -1L;
        if (manifestPresent) {
            BackupManifest manifest = manifests.readManifest(root);
            manifestValid = true;
            manifestTotalBytes = manifest.totalBytes();
            verified = manifests.hasCurrentVerification(root);
        }
        boolean valid = baseRestorable && (!manifestPresent || manifestValid);
        return new BackupSummary(root.getFileName().toString(), operation.createdAtEpochMillis(),
                operation.type().name().toLowerCase(java.util.Locale.ROOT),
                operation.sourceTerrain() == null ? "unknown" : operation.sourceTerrain().serializedName(),
                valid && manifestPresent ? manifestTotalBytes : -1L, Files.exists(root.resolve(".pinned")),
                baseRestorable && manifestValid && verified,
                valid,
                manifestPresent,
                verified,
                !manifestPresent);
    }

    public record BackupSummary(
            String id,
            long createdAtEpochMillis,
            String operation,
            String terrain,
            long sizeBytes,
            boolean pinned,
            boolean restorable,
            boolean valid,
            boolean manifestPresent,
            boolean verified,
            boolean legacy) {
        /** Source-compatible constructor for pre-manifest callers. */
        public BackupSummary(
                String id,
                long createdAtEpochMillis,
                String operation,
                String terrain,
                long sizeBytes,
                boolean pinned,
                boolean restorable,
                boolean valid) {
            this(id, createdAtEpochMillis, operation, terrain, sizeBytes, pinned, restorable, valid,
                    false, false, true);
        }

        public Instant createdAt() {
            return Instant.ofEpochMilli(createdAtEpochMillis);
        }
    }
}
