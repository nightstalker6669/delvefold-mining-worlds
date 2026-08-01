package com.nightsta69.delvefold.internal.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Shared forced-write and atomic-move primitives for Delvefold persistence. */
public final class AtomicFiles {
    private AtomicFiles() {}

    /**
     * Writes bytes through a forced temporary sibling and replaces the target.
     *
     * <p>The target's parent directories are created before the temporary file. The move is atomic where the file
     * system supports it and otherwise falls back to an ordinary replacing move. A temporary file left by a failed
     * write or move is deleted before the failure is returned.
     *
     * @param target destination file whose existing contents may be replaced
     * @param bytes complete bytes to persist
     * @throws IOException if directory creation, temporary persistence, replacement, or cleanup fails
     */
    public static void writeReplacing(Path target, byte[] bytes) throws IOException {
        writeReplacing(target, bytes, AtomicFiles::fileSystemMove);
    }

    /**
     * Writes bytes through a forced temporary sibling without requesting replacement of the target.
     *
     * <p>The target's parent directories are created before the temporary file. The move is atomic where the file
     * system supports it and otherwise falls back to an ordinary move with no copy options. A temporary file left by a
     * failed write or move is deleted before the failure is returned.
     *
     * @param target destination file for the persisted bytes
     * @param bytes complete bytes to persist
     * @throws IOException if directory creation, temporary persistence, installation, or cleanup fails
     */
    public static void writeWithoutReplaceOption(Path target, byte[] bytes) throws IOException {
        writeWithoutReplaceOption(target, bytes, AtomicFiles::fileSystemMove);
    }

    /**
     * Atomically moves a source over a destination, falling back only when atomic moves are unsupported.
     *
     * @param source file or directory to move
     * @param destination destination that may be replaced
     * @throws IOException if neither the atomic replacing move nor its supported fallback succeeds
     */
    public static void moveReplacing(Path source, Path destination) throws IOException {
        moveReplacing(source, destination, AtomicFiles::fileSystemMove);
    }

    /**
     * Atomically moves a source without requesting replacement, falling back only when atomic moves are unsupported.
     *
     * @param source file or directory to move
     * @param destination destination passed to moves without a replace option
     * @throws IOException if neither the atomic move nor its supported fallback succeeds
     */
    public static void moveWithoutReplaceOption(Path source, Path destination) throws IOException {
        moveWithoutReplaceOption(source, destination, AtomicFiles::fileSystemMove);
    }

    static void writeReplacing(Path target, byte[] bytes, MoveOperation moveOperation) throws IOException {
        write(target, bytes, moveOperation, true);
    }

    static void writeWithoutReplaceOption(Path target, byte[] bytes, MoveOperation moveOperation) throws IOException {
        write(target, bytes, moveOperation, false);
    }

    static void moveReplacing(Path source, Path destination, MoveOperation moveOperation) throws IOException {
        try {
            moveOperation.move(
                    source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            moveOperation.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static void moveWithoutReplaceOption(Path source, Path destination, MoveOperation moveOperation)
            throws IOException {
        try {
            moveOperation.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            moveOperation.move(source, destination);
        }
    }

    private static void write(Path target, byte[] bytes, MoveOperation moveOperation, boolean replace)
            throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary =
                Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        boolean moved = false;
        try {
            try (FileChannel channel =
                    FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            if (replace) {
                moveReplacing(temporary, target, moveOperation);
            } else {
                moveWithoutReplaceOption(temporary, target, moveOperation);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static void fileSystemMove(Path source, Path destination, CopyOption... options) throws IOException {
        Files.move(source, destination, options);
    }

    /** Injectable move boundary used by focused filesystem tests. */
    @FunctionalInterface
    interface MoveOperation {
        /**
         * Moves one path with the exact options selected by the persistence primitive.
         *
         * @param source source path
         * @param destination destination path
         * @param options copy options passed to the filesystem provider
         * @throws IOException if the move cannot be completed
         */
        void move(Path source, Path destination, CopyOption... options) throws IOException;
    }
}
