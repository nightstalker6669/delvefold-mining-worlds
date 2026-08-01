package com.nightsta69.delvefold.reset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.admin.AdminLocalizedMessage;
import com.nightsta69.delvefold.config.ConfigJson;
import com.nightsta69.delvefold.config.OrePresets;
import com.nightsta69.delvefold.config.model.GameplayPreset;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.TerrainVariant;
import com.nightsta69.delvefold.config.model.WorldIdentitySettings;
import com.nightsta69.delvefold.config.model.WorldSettingsDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackupManifestServiceTest {
    private static final String BACKUP_ID = "20260801-120000-11111111-1111-1111-1111-111111111111";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-01T12:30:00Z"), ZoneOffset.UTC);

    @TempDir
    Path saveRoot;

    @Test
    void manifestUsesSortedNormalizedPathsAndDetectsContentCorruption() throws Exception {
        Path backup = createLegacyBackup(BACKUP_ID);
        Files.writeString(backup.resolve(".pinned"), "pinned\n");
        Files.writeString(backup.resolve(RestoreConfigSnapshot.COMPLETE_MARKER), "complete\n");
        BackupManifestService service = new BackupManifestService(CLOCK);

        BackupManifestService.Verification verification = service.createVerifiedManifest(backup);

        List<String> paths = verification.manifest().files().stream()
                .map(BackupManifest.FileEntry::path)
                .toList();
        assertEquals(paths.stream().sorted().toList(), paths);
        assertTrue(paths.stream().allMatch(path -> !path.startsWith("/") && !path.contains("\\")));
        assertFalse(paths.contains(BackupManifest.FILE_NAME));
        assertFalse(paths.contains(BackupVerificationReceipt.FILE_NAME));
        assertFalse(paths.contains(".pinned"));
        assertFalse(paths.contains(RestoreConfigSnapshot.COMPLETE_MARKER));
        assertEquals(BACKUP_ID, verification.manifest().metadata().backupId());
        assertEquals("recreate", verification.manifest().metadata().operation());
        assertEquals("flat", verification.manifest().metadata().terrain());
        assertEquals(
                "6e5d1d2cb69b90129e3eb425a414e7880846e098dc7bb605eb85485aff47f40c",
                verification.manifest().files().stream()
                        .filter(entry -> entry.path().endsWith("r.0.0.mca"))
                        .findFirst()
                        .orElseThrow()
                        .sha256());
        assertEquals(
                verification.manifest().totalBytes(), verification.receipt().totalBytes());
        assertTrue(service.hasCurrentVerification(backup));

        Files.writeString(backup.resolve("dimensions/delvefold/delve_flat/region/r.0.0.mca"), "corrupt");
        IOException failure = assertThrows(IOException.class, () -> service.verify(backup));
        assertTrue(Objects.requireNonNull(failure.getMessage()).contains("SHA-256"));
        assertFalse(service.hasCurrentVerification(backup));
        assertFalse(Files.exists(backup.resolve(BackupVerificationReceipt.FILE_NAME)));
    }

    @Test
    void currentVerificationRejectsSameSizeManifestTamperingWithPreservedMtime() throws Exception {
        Path backup = createLegacyBackup(BACKUP_ID);
        BackupManifestService service = new BackupManifestService(CLOCK);
        service.createVerifiedManifest(backup);
        Path manifest = backup.resolve(BackupManifest.FILE_NAME);
        FileTime originalModified = Files.getLastModifiedTime(manifest);
        long originalSize = Files.size(manifest);
        String original = Files.readString(manifest);
        String tampered = original.replace("\"operation\": \"recreate\"", "\"operation\": \"recreatf\"");
        assertNotEquals(original, tampered);
        assertEquals(original.length(), tampered.length());

        Files.writeString(manifest, tampered);
        assertEquals(originalSize, Files.size(manifest));
        Files.setLastModifiedTime(manifest, originalModified);

        assertFalse(service.hasCurrentVerification(backup));
    }

    @Test
    void rejectsManifestTraversalAndUnexpectedFiles() throws Exception {
        Path backup = createLegacyBackup(BACKUP_ID);
        BackupManifestService service = new BackupManifestService(CLOCK);
        BackupManifest original = service.createVerifiedManifest(backup).manifest();
        BackupManifest.FileEntry first = original.files().getFirst();
        BackupManifest unsafe = new BackupManifest(
                original.schemaVersion(),
                original.hashAlgorithm(),
                original.metadata(),
                original.totalBytes(),
                List.of(new BackupManifest.FileEntry("../outside", first.sizeBytes(), first.sha256())));
        Files.writeString(backup.resolve(BackupManifest.FILE_NAME), ConfigJson.GSON.toJson(unsafe));

        assertThrows(IOException.class, () -> service.verify(backup));
        assertFalse(service.hasCurrentVerification(backup));

        Files.delete(backup.resolve(BackupManifest.FILE_NAME));
        service.createVerifiedManifest(backup);
        Files.writeString(backup.resolve("unexpected.dat"), "not manifested");
        IOException unexpected = assertThrows(IOException.class, () -> service.verify(backup));
        assertTrue(Objects.requireNonNull(unexpected.getMessage()).contains("not present"));
    }

    @Test
    void legacyUpgradeIsExplicitAndRunsOnTheProvidedWorker() throws Exception {
        Path backup = createLegacyBackup(BACKUP_ID);
        ExecutorService executor = Executors.newSingleThreadExecutor(task -> new Thread(task, "manifest-test-worker"));
        try {
            BackupVerificationService service = new BackupVerificationService(saveRoot, executor, CLOCK);

            BackupVerificationResult legacy = service.verifyAsync(BACKUP_ID).get(10, TimeUnit.SECONDS);
            assertEquals(BackupVerificationResult.Status.LEGACY_REQUIRES_VALIDATION, legacy.status());
            assertEquals(
                    "message.delvefold.backup_verification.legacy_requires_validation",
                    translationKey(legacy.message()));
            assertFalse(Files.exists(backup.resolve(BackupManifest.FILE_NAME)));

            BackupVerificationResult upgraded =
                    service.validateLegacyAndCreateManifestAsync(BACKUP_ID).get(10, TimeUnit.SECONDS);
            assertEquals(BackupVerificationResult.Status.LEGACY_UPGRADED, upgraded.status());
            assertEquals("message.delvefold.backup_verification.legacy_upgraded", translationKey(upgraded.message()));
            assertTrue(upgraded.successful());
            assertEquals("manifest-test-worker", upgraded.workerThread());
            assertNotEquals(Thread.currentThread().getName(), upgraded.workerThread());
            assertTrue(Files.isRegularFile(backup.resolve(BackupManifest.FILE_NAME)));
            assertTrue(new WorldBackupCatalog(saveRoot).list().getFirst().restorable());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void invalidLegacyBackupIsNeverPartiallyUpgraded() throws Exception {
        Path backup = createLegacyBackup(BACKUP_ID);
        Files.delete(backup.resolve("config/serverconfig/delvefold/ores.json"));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            BackupVerificationService service = new BackupVerificationService(saveRoot, executor, CLOCK);
            BackupVerificationResult result =
                    service.validateLegacyAndCreateManifestAsync(BACKUP_ID).get(10, TimeUnit.SECONDS);
            assertEquals(BackupVerificationResult.Status.FAILED, result.status());
            assertEquals("message.delvefold.backup_verification.failed", translationKey(result.message()));
            assertFalse(Files.exists(backup.resolve(BackupManifest.FILE_NAME)));
            assertFalse(Files.exists(backup.resolve(BackupVerificationReceipt.FILE_NAME)));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsEmptyUnknownAndMissingActiveDimensionSnapshots() throws Exception {
        BackupManifestService service = new BackupManifestService(CLOCK);

        Path empty = createLegacyBackup(BACKUP_ID + "-empty");
        Files.delete(empty.resolve("dimensions/delvefold/delve_flat/region/r.0.0.mca"));
        IOException emptyFailure = assertThrows(IOException.class, () -> service.validateLegacyLayout(empty));
        assertTrue(Objects.requireNonNull(emptyFailure.getMessage()).contains("no canonical Delvefold dimension data"));
        assertFalse(Files.exists(empty.resolve(BackupManifest.FILE_NAME)));

        Path unknown = createLegacyBackup(BACKUP_ID + "-unknown");
        Path unrelated = unknown.resolve("dimensions/delvefold/not_a_delvefold_dimension");
        Files.createDirectories(unrelated);
        Files.writeString(unrelated.resolve("region.dat"), "unrelated");
        IOException unknownFailure = assertThrows(IOException.class, () -> service.validateLegacyLayout(unknown));
        assertTrue(Objects.requireNonNull(unknownFailure.getMessage()).contains("unknown top-level dimension entry"));
        assertFalse(Files.exists(unknown.resolve(BackupManifest.FILE_NAME)));

        Path missingActive = createLegacyBackup(BACKUP_ID + "-missing-active");
        WorldSettingsDocument expansiveWild = WorldSettingsDocument.uninitialized()
                .initialize(
                        TerrainMode.WILD,
                        OrePreset.VANILLA_BALANCED,
                        GameplayPreset.SAFE,
                        WorldIdentitySettings.defaults().withTerrainVariant(TerrainVariant.EXPANSIVE));
        Files.writeString(
                missingActive.resolve("config/serverconfig/delvefold/settings.json"),
                ConfigJson.GSON.toJson(expansiveWild));
        IOException activeFailure = assertThrows(IOException.class, () -> service.validateLegacyLayout(missingActive));
        assertTrue(Objects.requireNonNull(activeFailure.getMessage()).contains("delve_wild_expansive"));

        Path activeRegion = missingActive.resolve("dimensions/delvefold/delve_wild_expansive/region/r.0.0.mca");
        Files.createDirectories(activeRegion.getParent());
        Files.writeString(activeRegion, "active-region-data");
        service.validateLegacyLayout(missingActive);
        assertTrue(service.createVerifiedManifest(missingActive).manifest().files().stream()
                .anyMatch(entry -> entry.path().equals("dimensions/delvefold/delve_wild_expansive/region/r.0.0.mca")));

        Path legacyExpansive = createLegacyBackup(BACKUP_ID + "-legacy-expansive");
        Files.move(
                legacyExpansive.resolve("dimensions/delvefold/delve_flat"),
                legacyExpansive.resolve("dimensions/delvefold/delve_flat_expansive"));
        service.validateLegacyLayout(legacyExpansive);
        assertTrue(service.createVerifiedManifest(legacyExpansive).manifest().files().stream()
                .anyMatch(entry -> entry.path().startsWith("dimensions/delvefold/delve_flat_expansive/")));
    }

    @Test
    void verificationSubmissionReturnsBeforeWorkerHashingAndDeduplicatesRequests() throws Exception {
        Path backup = createLegacyBackup(BACKUP_ID);
        new BackupManifestService(CLOCK).createVerifiedManifest(backup);
        AtomicReference<Runnable> captured = new AtomicReference<>();
        BackupVerificationService service = new BackupVerificationService(saveRoot, captured::set, CLOCK);

        CompletableFuture<BackupVerificationResult> first = service.verifyAsync(BACKUP_ID);
        CompletableFuture<BackupVerificationResult> duplicate = service.verifyAsync(BACKUP_ID);

        assertSame(first, duplicate);
        assertFalse(first.isDone());
        assertNotNull(captured.get());
        Thread worker = new Thread(captured.get(), "controlled-backup-worker");
        worker.start();
        worker.join(10_000L);
        BackupVerificationResult result = first.get(1, TimeUnit.SECONDS);
        assertEquals(BackupVerificationResult.Status.VERIFIED, result.status());
        assertEquals("message.delvefold.backup_verification.verified", translationKey(result.message()));
        assertEquals("controlled-backup-worker", result.workerThread());
    }

    @Test
    void differentActionsForOneBackupAreSerializedWithoutLosingEitherRequest() throws Exception {
        createLegacyBackup(BACKUP_ID);
        Queue<Runnable> queued = new ArrayDeque<>();
        BackupVerificationService service = new BackupVerificationService(saveRoot, queued::add, CLOCK);

        CompletableFuture<BackupVerificationResult> verify = service.verifyAsync(BACKUP_ID);
        CompletableFuture<BackupVerificationResult> upgrade = service.validateLegacyAndCreateManifestAsync(BACKUP_ID);

        assertEquals(1, queued.size(), "only the first action may reach the executor");
        assertTrue(service.isInFlight(BACKUP_ID));
        queued.remove().run();
        assertEquals(
                BackupVerificationResult.Status.LEGACY_REQUIRES_VALIDATION,
                verify.join().status());
        assertFalse(upgrade.isDone());
        assertEquals(1, queued.size(), "the second action is scheduled only after the first completes");

        queued.remove().run();
        assertEquals(
                BackupVerificationResult.Status.LEGACY_UPGRADED, upgrade.join().status());
        assertFalse(service.isInFlight(BACKUP_ID));
    }

    @Test
    void sharedFactoryNormalizesSaveRoots() {
        assertSame(
                BackupVerificationService.forSave(saveRoot), BackupVerificationService.forSave(saveRoot.resolve(".")));
    }

    private static String translationKey(String message) {
        return AdminLocalizedMessage.decode(message).orElseThrow().translationKey();
    }

    private Path createLegacyBackup(String id) throws Exception {
        Path backup = saveRoot.resolve("delvefold_backups").resolve(id);
        Files.createDirectories(backup.resolve("dimensions/delvefold/delve_flat/region"));
        Files.writeString(backup.resolve("dimensions/delvefold/delve_flat/region/r.0.0.mca"), "region-data");
        Files.createDirectories(backup.resolve("config/serverconfig/delvefold"));
        Files.writeString(
                backup.resolve("config/serverconfig/delvefold/settings.json"),
                ConfigJson.GSON.toJson(WorldSettingsDocument.uninitialized()));
        Files.writeString(
                backup.resolve("config/serverconfig/delvefold/ores.json"),
                ConfigJson.GSON.toJson(OrePresets.create(OrePreset.VANILLA_BALANCED)));
        PendingWorldOperation operation = new PendingWorldOperation(
                PendingWorldOperation.CURRENT_SCHEMA_VERSION,
                "11111111-1111-1111-1111-111111111111",
                WorldOperationType.RECREATE,
                TerrainMode.FLAT,
                TerrainMode.CAVERN,
                TerrainVariant.EXPANSIVE,
                null,
                null,
                BackupMode.KEEP_BACKUP,
                false,
                1234,
                "tester");
        Files.writeString(backup.resolve("operation.json"), ConfigJson.GSON.toJson(operation));
        return backup;
    }
}
