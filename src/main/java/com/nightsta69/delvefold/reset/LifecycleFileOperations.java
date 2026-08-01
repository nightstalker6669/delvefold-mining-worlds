package com.nightsta69.delvefold.reset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/** Shared, deliberately narrow filesystem primitives for restart-bound lifecycle transactions. */
final class LifecycleFileOperations {
    private LifecycleFileOperations() {}

    /**
     * Resolves and validates an existing directory without following or inspecting its ancestors.
     *
     * @param supplied directory supplied by the lifecycle caller, or {@code null}
     * @param description caller-owned text used in the compatibility-stable failure message
     * @return the absolute normalized directory
     * @throws IOException if the path is absent, is a final-component symbolic link, or is not a directory
     */
    static Path requireDirectory(@Nullable Path supplied, String description) throws IOException {
        if (supplied == null) {
            throw new IOException(description + " is missing");
        }
        Path path = supplied.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path)) {
            throw new IOException(description + " failed safety checks");
        }
        return path;
    }

    /**
     * Resolves a directory, creates it when absent, and validates only the final component for symbolic links.
     *
     * <p>{@link Files#createDirectories(Path, java.nio.file.attribute.FileAttribute[])} retains responsibility for
     * reporting an existing non-directory, including its {@link java.nio.file.FileAlreadyExistsException} behavior.
     *
     * @param supplied directory supplied by the lifecycle caller, or {@code null}
     * @param description caller-owned text used in the compatibility-stable failure message
     * @return the absolute normalized directory
     * @throws IOException if the path is absent, is a final-component symbolic link, cannot be created, or is not a
     *     directory after creation
     */
    static Path requireDirectoryOrCreate(@Nullable Path supplied, String description) throws IOException {
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

    /**
     * Deletes a file tree without following symbolic links.
     *
     * <p>A missing root is a successful no-op. Callers that reject a symbolic-link root must perform that
     * caller-specific check before invoking this primitive.
     *
     * @param root tree root to delete
     * @throws IOException if traversal or deletion fails
     */
    static void deleteTree(Path root) throws IOException {
        if (Files.notExists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
