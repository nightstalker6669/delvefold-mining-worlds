package com.nightsta69.delvefold.reset;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Stream;

/** Crash-safe configuration snapshot used by the pre-restore backup. */
final class RestoreConfigSnapshot {
    static final String COMPLETE_MARKER = ".config-complete";
    private static final String STAGING_DIRECTORY = ".config-staging";
    private static final Set<String> TRANSIENT_CONFIG_FILES =
            Set.of("pending_restore.json", "pending_world_operation.json", "config_transaction.json");
    private static final Set<String> OPERATIONAL_CONFIG_DIRECTORIES =
            Set.of("audit", "exports", "imports", "world_operations");

    private RestoreConfigSnapshot() {}

    static void capture(Path sourceDirectory, Path preRestoreRoot) throws IOException {
        Path source = checkedDirectory(sourceDirectory, "configuration source");
        Path root = checkedDirectoryOrCreate(preRestoreRoot, "pre-restore backup");
        Path marker = root.resolve(COMPLETE_MARKER);
        Path destination = root.resolve("config/serverconfig/delvefold");
        ensureSafeAncestors(root, destination.getParent());
        if (Files.exists(marker) || Files.isSymbolicLink(marker)) {
            if (Files.isSymbolicLink(marker)
                    || !Files.isRegularFile(marker)
                    || Files.isSymbolicLink(destination)
                    || !Files.isDirectory(destination)) {
                throw new IOException("Pre-restore configuration completion marker is inconsistent");
            }
            return;
        }

        Path staging = root.resolve(STAGING_DIRECTORY);
        deleteTree(staging);
        deleteTree(destination);
        copyTree(source, staging);
        Files.createDirectories(destination.getParent());
        move(staging, destination);
        Files.writeString(
                marker, "complete\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    /** Installs only restorable configuration while preserving live operational history. */
    static void install(Path stagedDirectory, Path activeDirectory) throws IOException {
        Path source = checkedDirectory(stagedDirectory, "staged configuration");
        Path destination = checkedDirectoryOrCreate(activeDirectory, "active configuration");
        copyTree(source, destination);
    }

    /** Removes a snapshot created by an uncommitted backup transaction so a retry can recapture it. */
    static void discardCapture(Path preRestoreRoot) throws IOException {
        Path root = checkedDirectory(preRestoreRoot, "pre-restore backup");
        Path marker = root.resolve(COMPLETE_MARKER);
        if (Files.isSymbolicLink(marker)) {
            throw new IOException("Pre-restore configuration completion marker failed safety checks");
        }
        Files.deleteIfExists(marker);
        deleteTree(root.resolve(STAGING_DIRECTORY));
        deleteTree(root.resolve("config/serverconfig/delvefold"));
    }

    private static void copyTree(Path sourceRoot, Path destinationRoot) throws IOException {
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            for (Path source : paths.toList()) {
                if (Files.isSymbolicLink(source)) {
                    throw new IOException("Configuration snapshot source contains a symbolic link");
                }
                Path relative = sourceRoot.relativize(source);
                if (isOperationalConfigPath(relative)) {
                    continue;
                }
                if (relative.getNameCount() == 1 && TRANSIENT_CONFIG_FILES.contains(relative.toString())) {
                    continue;
                }
                Path destination = destinationRoot.resolve(relative).normalize();
                if (!destination.startsWith(destinationRoot.normalize())) {
                    throw new IOException("Configuration snapshot path escaped its staging directory");
                }
                ensureSafeAncestors(destinationRoot, destination.getParent());
                if (Files.isSymbolicLink(destination)) {
                    throw new IOException("Configuration snapshot destination contains a symbolic link");
                }
                if (Files.isDirectory(source)) {
                    if (Files.exists(destination) && !Files.isDirectory(destination)) {
                        throw new IOException("Configuration snapshot destination contains a non-directory");
                    }
                    Files.createDirectories(destination);
                } else if (Files.isRegularFile(source)) {
                    if (Files.exists(destination) && !Files.isRegularFile(destination)) {
                        throw new IOException("Configuration snapshot destination contains a non-regular file");
                    }
                    Files.createDirectories(destination.getParent());
                    Files.copy(
                            source,
                            destination,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES);
                } else {
                    throw new IOException("Configuration snapshot source contains a non-regular file");
                }
            }
        }
    }

    static boolean isOperationalConfigPath(Path relative) {
        return relative != null
                && relative.getNameCount() > 0
                && OPERATIONAL_CONFIG_DIRECTORIES.contains(relative.getName(0).toString());
    }

    private static Path checkedDirectory(Path supplied, String description) throws IOException {
        if (supplied == null) {
            throw new IOException(description + " is missing");
        }
        Path path = supplied.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path)) {
            throw new IOException(description + " failed safety checks");
        }
        return path;
    }

    private static Path checkedDirectoryOrCreate(Path supplied, String description) throws IOException {
        if (supplied == null) {
            throw new IOException(description + " is missing");
        }
        Path path = supplied.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(path)) {
            throw new IOException(description + " failed safety checks");
        }
        Files.createDirectories(path);
        if (!Files.isDirectory(path)) {
            throw new IOException(description + " failed safety checks");
        }
        return path;
    }

    private static void ensureSafeAncestors(Path root, Path target) throws IOException {
        Path current = root;
        for (Path part : root.relativize(target)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current) || (Files.exists(current) && !Files.isDirectory(current))) {
                throw new IOException("Pre-restore configuration path contains an unsafe ancestor");
            }
        }
    }

    private static void move(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (Files.notExists(root)) {
            return;
        }
        if (Files.isSymbolicLink(root)) {
            throw new IOException("Refusing to delete symbolic-link configuration staging");
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
