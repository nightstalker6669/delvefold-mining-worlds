package com.nightsta69.delvefold.qualityruntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Source contracts that keep persistence and worker-thread compatibility logic in its reviewed primitives. */
class SharedInfrastructureCharacterizationTest {
    private static final Path PRODUCTION_ROOT = Path.of("src/main/java/com/nightsta69/delvefold");

    @Test
    void atomicMoveFallbackIsOwnedOnlyByAtomicFiles() throws IOException {
        List<Path> owners = ProductionSources.allJavaFiles().stream()
                .filter(path -> contains(path, "AtomicMoveNotSupportedException"))
                .map(PRODUCTION_ROOT::relativize)
                .toList();

        assertEquals(List.of(Path.of("internal/io/AtomicFiles.java")), owners);
    }

    @Test
    void daemonThreadConstructionIsOwnedOnlyByNamedFactory() throws IOException {
        List<Path> constructors = ProductionSources.allJavaFiles().stream()
                .filter(path -> contains(path, "new Thread("))
                .map(PRODUCTION_ROOT::relativize)
                .toList();
        List<Path> daemonSetters = ProductionSources.allJavaFiles().stream()
                .filter(path -> contains(path, ".setDaemon(true)"))
                .map(PRODUCTION_ROOT::relativize)
                .toList();

        Path factory = Path.of("internal/concurrent/NamedDaemonThreadFactory.java");
        assertEquals(List.of(factory), constructors);
        assertEquals(List.of(factory), daemonSetters);
    }

    @Test
    void reviewedPersistenceCallersUseTheSharedAtomicPrimitive() throws IOException {
        for (String relativePath : List.of(
                "audit/RotatingAuditLog.java",
                "config/FileConfigRepository.java",
                "config/OreProfileCatalog.java",
                "diagnostics/DoctorReportExporter.java",
                "reset/BackupManifestService.java",
                "reset/RestoreConfigSnapshot.java",
                "reset/RestoreCurrentBackupTransaction.java",
                "reset/WorldOperationService.java",
                "reset/WorldRestoreService.java")) {
            assertTrue(
                    ProductionSources.read(relativePath).contains("AtomicFiles."),
                    () -> "Reviewed persistence caller bypasses AtomicFiles: " + relativePath);
        }
    }

    private static boolean contains(Path path, String fragment) {
        try {
            return java.nio.file.Files.readString(path).contains(fragment);
        } catch (IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }
}
