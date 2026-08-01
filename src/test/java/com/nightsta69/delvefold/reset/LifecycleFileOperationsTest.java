package com.nightsta69.delvefold.reset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Characterization tests for lifecycle directory validation and non-following tree deletion. */
class LifecycleFileOperationsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void existingDirectoryValidationPreservesNormalizationAndFailureMessages() throws Exception {
        Path directory = Files.createDirectories(temporaryDirectory.resolve("parent/child"));
        Path unnormalized = directory.resolve("../child");

        assertEquals(
                unnormalized.toAbsolutePath().normalize(),
                LifecycleFileOperations.requireDirectory(unnormalized, "Active dimension root"));
        assertEquals(
                "Active dimension root is missing",
                assertThrows(
                                IOException.class,
                                () -> LifecycleFileOperations.requireDirectory(null, "Active dimension root"))
                        .getMessage());
        assertEquals(
                "Active dimension root failed safety checks",
                assertThrows(
                                IOException.class,
                                () -> LifecycleFileOperations.requireDirectory(
                                        temporaryDirectory.resolve("absent"), "Active dimension root"))
                        .getMessage());

        Path regularFile = temporaryDirectory.resolve("regular-file");
        Files.writeString(regularFile, "not a directory");
        assertEquals(
                "Active dimension root failed safety checks",
                assertThrows(
                                IOException.class,
                                () -> LifecycleFileOperations.requireDirectory(regularFile, "Active dimension root"))
                        .getMessage());
    }

    @Test
    void createValidationPreservesCreateDirectoriesBehaviorAndCheckOrder() throws Exception {
        Path requested = temporaryDirectory.resolve("new/../created");
        assertEquals(
                requested.toAbsolutePath().normalize(),
                LifecycleFileOperations.requireDirectoryOrCreate(requested, "Pre-restore backup root"));
        assertTrue(Files.isDirectory(temporaryDirectory.resolve("created")));

        assertEquals(
                "Pre-restore backup root is missing",
                assertThrows(
                                IOException.class,
                                () -> LifecycleFileOperations.requireDirectoryOrCreate(null, "Pre-restore backup root"))
                        .getMessage());

        Path regularFile = temporaryDirectory.resolve("existing-file");
        Files.writeString(regularFile, "not a directory");
        assertThrows(
                FileAlreadyExistsException.class,
                () -> LifecycleFileOperations.requireDirectoryOrCreate(regularFile, "Pre-restore backup root"));

        Path linkTarget = Files.createDirectories(temporaryDirectory.resolve("link-target"));
        Path link = temporaryDirectory.resolve("final-link");
        Files.createSymbolicLink(link, linkTarget);
        assertEquals(
                "Pre-restore backup root failed safety checks",
                assertThrows(
                                IOException.class,
                                () -> LifecycleFileOperations.requireDirectoryOrCreate(link, "Pre-restore backup root"))
                        .getMessage());
    }

    @Test
    void validationChecksOnlyTheFinalPathComponentForSymbolicLinks() throws Exception {
        Path external = Files.createDirectories(temporaryDirectory.resolve("external"));
        Path ancestorLink = temporaryDirectory.resolve("ancestor-link");
        Files.createSymbolicLink(ancestorLink, external);

        Path existingThroughLink = Files.createDirectories(external.resolve("existing"));
        Path suppliedExisting = ancestorLink.resolve("existing");
        assertEquals(
                suppliedExisting.toAbsolutePath().normalize(),
                LifecycleFileOperations.requireDirectory(suppliedExisting, "configuration source"));
        assertEquals(existingThroughLink.toRealPath(), suppliedExisting.toRealPath());

        Path suppliedNew = ancestorLink.resolve("created");
        LifecycleFileOperations.requireDirectoryOrCreate(suppliedNew, "active configuration");
        assertTrue(Files.isDirectory(external.resolve("created")));
    }

    @Test
    void deletionIsMissingSafeReverseOrderedAndDoesNotFollowLinks() throws Exception {
        Path missing = temporaryDirectory.resolve("missing-tree");
        LifecycleFileOperations.deleteTree(missing);

        Path external = Files.createDirectories(temporaryDirectory.resolve("external-delete-target"));
        Files.writeString(external.resolve("preserved.txt"), "preserved");
        Path root = Files.createDirectories(temporaryDirectory.resolve("tree/a/b"));
        Files.writeString(root.resolve("nested.txt"), "nested");
        Files.createSymbolicLink(temporaryDirectory.resolve("tree/a/external-link"), external);

        LifecycleFileOperations.deleteTree(temporaryDirectory.resolve("tree"));

        assertFalse(Files.exists(temporaryDirectory.resolve("tree")));
        assertEquals("preserved", Files.readString(external.resolve("preserved.txt")));
    }

    @Test
    void primitiveDeletesARootLinkButRestoreCallersKeepTheirSpecificRejections() throws Exception {
        Path external = Files.createDirectories(temporaryDirectory.resolve("external-root"));
        Files.writeString(external.resolve("preserved.txt"), "preserved");
        Path rootLink = temporaryDirectory.resolve("root-link");
        Files.createSymbolicLink(rootLink, external);

        LifecycleFileOperations.deleteTree(rootLink);

        assertFalse(Files.exists(rootLink));
        assertEquals("preserved", Files.readString(external.resolve("preserved.txt")));

        Path source = Files.createDirectories(temporaryDirectory.resolve("source"));
        Files.writeString(source.resolve("settings.json"), "settings");
        Path backup = Files.createDirectories(temporaryDirectory.resolve("backup"));
        Path stagingLink = backup.resolve(".config-staging");
        Files.createSymbolicLink(stagingLink, external);
        assertEquals(
                "Refusing to delete symbolic-link configuration staging",
                assertThrows(IOException.class, () -> RestoreConfigSnapshot.capture(source, backup))
                        .getMessage());

        String restoreSource =
                Files.readString(Path.of("src/main/java/com/nightsta69/delvefold/reset/WorldRestoreService.java"));
        assertTrue(restoreSource.contains("Refusing to delete symbolic-link restore staging"));
        assertTrue(restoreSource.contains("LifecycleFileOperations.deleteTree(root)"));
    }
}
