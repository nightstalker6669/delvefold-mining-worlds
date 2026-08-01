package com.nightsta69.delvefold.internal.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicFilesTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void writeReplacingCreatesParentsPersistsEveryByteAndReplacesExistingContents() throws Exception {
        Path target = temporaryDirectory.resolve("nested/state.json");
        byte[] first = {1, 2, 3, 4};
        byte[] replacement = {9, 8, 7};

        AtomicFiles.writeReplacing(target, first);
        assertArrayEquals(first, Files.readAllBytes(target));

        AtomicFiles.writeReplacing(target, replacement);
        assertArrayEquals(replacement, Files.readAllBytes(target));
        assertNoTemporarySiblings(target);
    }

    @Test
    void writeWithoutReplaceOptionCreatesParentsAndPersistsEveryByte() throws Exception {
        Path target = temporaryDirectory.resolve("new/catalog.json");
        byte[] expected = {5, 4, 3, 2, 1};

        AtomicFiles.writeWithoutReplaceOption(target, expected);

        assertArrayEquals(expected, Files.readAllBytes(target));
        assertNoTemporarySiblings(target);
    }

    @Test
    void replacingMoveReplacesAnExistingDestination() throws Exception {
        Path source = Files.writeString(temporaryDirectory.resolve("source"), "new");
        Path destination = Files.writeString(temporaryDirectory.resolve("destination"), "old");

        AtomicFiles.moveReplacing(source, destination);

        assertFalse(Files.exists(source));
        assertEquals("new", Files.readString(destination));
    }

    @Test
    void moveWithoutReplaceOptionMovesToANewDestination() throws Exception {
        Path source = Files.writeString(temporaryDirectory.resolve("source"), "contents");
        Path destination = temporaryDirectory.resolve("destination");

        AtomicFiles.moveWithoutReplaceOption(source, destination);

        assertFalse(Files.exists(source));
        assertEquals("contents", Files.readString(destination));
    }

    @Test
    void replacingMoveFallsBackWithOnlyReplaceExistingWhenAtomicMoveIsUnsupported() throws Exception {
        Path source = Files.writeString(temporaryDirectory.resolve("source"), "new");
        Path destination = Files.writeString(temporaryDirectory.resolve("destination"), "old");
        List<List<CopyOption>> attempts = new ArrayList<>();

        AtomicFiles.moveReplacing(source, destination, (attemptSource, attemptDestination, options) -> {
            attempts.add(List.of(options));
            if (attempts.size() == 1) {
                throw new AtomicMoveNotSupportedException(
                        attemptSource.toString(), attemptDestination.toString(), "simulated");
            }
            Files.move(attemptSource, attemptDestination, options);
        });

        assertEquals(
                List.of(
                        List.of(StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING),
                        List.of(StandardCopyOption.REPLACE_EXISTING)),
                attempts);
        assertFalse(Files.exists(source));
        assertEquals("new", Files.readString(destination));
    }

    @Test
    void noReplaceWriteFallsBackWithNoOptionsAndPreservesAnExistingTarget() throws Exception {
        Path target = Files.writeString(temporaryDirectory.resolve("state.json"), "original");
        List<List<CopyOption>> attempts = new ArrayList<>();

        assertThrows(
                FileAlreadyExistsException.class,
                () -> AtomicFiles.writeWithoutReplaceOption(
                        target, new byte[] {6, 7}, (source, destination, options) -> {
                            attempts.add(List.of(options));
                            if (attempts.size() == 1) {
                                throw new AtomicMoveNotSupportedException(
                                        source.toString(), destination.toString(), "simulated");
                            }
                            Files.move(source, destination, options);
                        }));

        assertEquals(List.of(List.of(StandardCopyOption.ATOMIC_MOVE), List.of()), attempts);
        assertEquals("original", Files.readString(target));
        assertNoTemporarySiblings(target);
    }

    @Test
    void unrelatedMoveFailuresArePropagatedWithoutFallback() throws Exception {
        Path source = Files.writeString(temporaryDirectory.resolve("source"), "contents");
        Path destination = temporaryDirectory.resolve("destination");
        IOException failure = new IOException("simulated I/O failure");
        int[] attempts = {0};

        IOException thrown = assertThrows(
                IOException.class,
                () -> AtomicFiles.moveReplacing(source, destination, (ignoredSource, ignoredDestination, options) -> {
                    attempts[0]++;
                    throw failure;
                }));

        assertSame(failure, thrown);
        assertEquals(1, attempts[0]);
        assertTrue(Files.exists(source));
        assertFalse(Files.exists(destination));

        Path noReplaceSource = Files.writeString(temporaryDirectory.resolve("no-replace-source"), "contents");
        Path noReplaceDestination = temporaryDirectory.resolve("no-replace-destination");
        int[] noReplaceAttempts = {0};

        IOException noReplaceThrown = assertThrows(
                IOException.class,
                () -> AtomicFiles.moveWithoutReplaceOption(
                        noReplaceSource, noReplaceDestination, (ignoredSource, ignoredDestination, options) -> {
                            noReplaceAttempts[0]++;
                            throw failure;
                        }));

        assertSame(failure, noReplaceThrown);
        assertEquals(1, noReplaceAttempts[0]);
        assertTrue(Files.exists(noReplaceSource));
        assertFalse(Files.exists(noReplaceDestination));
    }

    @Test
    void failedWriteDeletesItsTemporarySibling() throws Exception {
        Path target = temporaryDirectory.resolve("nested/state.json");
        IOException failure = new IOException("simulated move failure");

        IOException thrown = assertThrows(
                IOException.class,
                () -> AtomicFiles.writeReplacing(target, new byte[] {1, 2, 3}, (source, destination, options) -> {
                    throw failure;
                }));

        assertSame(failure, thrown);
        assertFalse(Files.exists(target));
        assertNoTemporarySiblings(target);
    }

    private static void assertNoTemporarySiblings(Path target) throws IOException {
        try (Stream<Path> entries = Files.list(target.getParent())) {
            assertFalse(entries.anyMatch(path -> {
                String filename = path.getFileName().toString();
                return filename.startsWith(target.getFileName().toString()) && filename.endsWith(".tmp");
            }));
        }
    }
}
