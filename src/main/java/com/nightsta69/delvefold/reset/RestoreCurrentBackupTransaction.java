package com.nightsta69.delvefold.reset;

import com.nightsta69.delvefold.config.ConfigJson;
import com.nightsta69.delvefold.config.model.WorldSettingsDocument;
import com.nightsta69.delvefold.internal.io.AtomicFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Transactional creation of the pre-restore snapshot while the server is offline. */
final class RestoreCurrentBackupTransaction {
    private RestoreCurrentBackupTransaction() {}

    static void backup(Path configDirectory, Path activeRoot, Path preRestore, PendingWorldRestore pending)
            throws IOException {
        backup(configDirectory, activeRoot, preRestore, pending, RestoreCurrentBackupTransaction::moveDirectory);
    }

    static void backup(
            Path configDirectory, Path activeRoot, Path preRestore, PendingWorldRestore pending, DirectoryMover mover)
            throws IOException {
        Objects.requireNonNull(pending, "pending");
        Objects.requireNonNull(mover, "mover");
        Path config = LifecycleFileOperations.requireDirectory(configDirectory, "Active configuration");
        Path activeDimensions = LifecycleFileOperations.requireDirectory(activeRoot, "Active dimension root");
        Path backup = LifecycleFileOperations.requireDirectoryOrCreate(preRestore, "Pre-restore backup root");
        Path backupRoot = backup.resolve("dimensions/delvefold").normalize();
        if (!backupRoot.startsWith(backup)) {
            throw new IOException("Pre-restore dimension path escaped its backup root");
        }
        Files.createDirectories(backupRoot);
        preflightDimensionMoves(activeDimensions, backupRoot);

        Path marker = LifecycleJournalFiles.operationMarker(backup);
        if (Files.exists(marker) || Files.isSymbolicLink(marker)) {
            if (Files.isSymbolicLink(marker) || !Files.isRegularFile(marker)) {
                throw new IOException("Pre-restore operation marker failed safety checks");
            }
        } else {
            WorldSettingsDocument settings = readSettings(config.resolve("settings.json"));
            PendingWorldOperation metadata = new PendingWorldOperation(
                    PendingWorldOperation.CURRENT_SCHEMA_VERSION,
                    pending.operationId(),
                    WorldOperationType.RECREATE,
                    settings.terrainMode(),
                    settings.terrainMode(),
                    settings.identity().terrainVariant(),
                    settings.identity().geologyTheme(),
                    settings.orePreset(),
                    settings.gameplay().preset(),
                    BackupMode.KEEP_BACKUP,
                    false,
                    pending.createdAtEpochMillis(),
                    pending.requestedBy());
            writeJson(marker, metadata);
        }

        boolean configSnapshotExisted = Files.exists(backup.resolve(RestoreConfigSnapshot.COMPLETE_MARKER))
                || Files.isSymbolicLink(backup.resolve(RestoreConfigSnapshot.COMPLETE_MARKER));
        RestoreConfigSnapshot.capture(config, backup);
        boolean manifestExisted = Files.exists(backup.resolve(BackupManifest.FILE_NAME))
                || Files.isSymbolicLink(backup.resolve(BackupManifest.FILE_NAME));
        List<DimensionMove> moved = new ArrayList<>();
        try {
            for (String dimension : DelvefoldDimensionFolders.ALL) {
                Path active = activeDimensions.resolve(dimension);
                Path destination = backupRoot.resolve(dimension);
                if (Files.isDirectory(active) && Files.notExists(destination)) {
                    mover.move(active, destination);
                    moved.add(new DimensionMove(active, destination));
                }
            }
            new BackupManifestService().createVerifiedManifest(backup);
        } catch (IOException | RuntimeException failure) {
            IOException transactionFailure = failure instanceof IOException ioFailure
                    ? ioFailure
                    : new IOException("Pre-restore backup transaction failed", failure);
            rollbackDimensionMoves(moved, mover, transactionFailure);
            if (!manifestExisted) {
                deleteFreshManifestControls(backup, transactionFailure);
            }
            if (!configSnapshotExisted) {
                try {
                    RestoreConfigSnapshot.discardCapture(backup);
                } catch (IOException cleanupFailure) {
                    transactionFailure.addSuppressed(cleanupFailure);
                }
            }
            throw transactionFailure;
        }
    }

    private static void preflightDimensionMoves(Path activeRoot, Path backupRoot) throws IOException {
        for (String dimension : DelvefoldDimensionFolders.ALL) {
            Path active = activeRoot.resolve(dimension);
            Path backup = backupRoot.resolve(dimension);
            boolean activePresent = Files.exists(active) || Files.isSymbolicLink(active);
            boolean backupPresent = Files.exists(backup) || Files.isSymbolicLink(backup);
            if (activePresent && (Files.isSymbolicLink(active) || !Files.isDirectory(active))) {
                throw new IOException("Active dimension failed safety checks: " + dimension);
            }
            if (backupPresent && (Files.isSymbolicLink(backup) || !Files.isDirectory(backup))) {
                throw new IOException("Pre-restore dimension failed safety checks: " + dimension);
            }
            if (activePresent && backupPresent) {
                throw new IOException("Active and pre-restore dimension both exist: " + dimension);
            }
        }
    }

    private static void rollbackDimensionMoves(
            List<DimensionMove> moved, DirectoryMover mover, IOException transactionFailure) {
        for (int index = moved.size() - 1; index >= 0; index--) {
            DimensionMove move = moved.get(index);
            try {
                Files.createDirectories(move.active().getParent());
                mover.move(move.backup(), move.active());
            } catch (IOException | RuntimeException rollbackFailure) {
                transactionFailure.addSuppressed(rollbackFailure);
            }
        }
    }

    private static void deleteFreshManifestControls(Path backup, IOException transactionFailure) {
        for (String file : List.of(BackupVerificationReceipt.FILE_NAME, BackupManifest.FILE_NAME)) {
            Path control = backup.resolve(file);
            try {
                if (Files.isSymbolicLink(control)) {
                    throw new IOException("Refusing to remove a symbolic-link backup control file");
                }
                Files.deleteIfExists(control);
            } catch (IOException cleanupFailure) {
                transactionFailure.addSuppressed(cleanupFailure);
            }
        }
    }

    private static WorldSettingsDocument readSettings(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path)) {
            throw new IOException("Active settings failed safety checks");
        }
        WorldSettingsDocument settings =
                ConfigJson.GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), WorldSettingsDocument.class);
        if (settings == null || !settings.initialized()) {
            throw new IOException("Active settings are not initialized");
        }
        return settings;
    }

    private static void writeJson(Path target, Object value) throws IOException {
        byte[] bytes = (ConfigJson.GSON.toJson(value) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        AtomicFiles.writeWithoutReplaceOption(target, bytes);
    }

    private static void moveDirectory(Path source, Path destination) throws IOException {
        Files.createDirectories(destination.getParent());
        AtomicFiles.moveWithoutReplaceOption(source, destination);
    }

    private record DimensionMove(Path active, Path backup) {}

    @FunctionalInterface
    interface DirectoryMover {
        void move(Path source, Path destination) throws IOException;
    }
}
