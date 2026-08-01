package com.nightsta69.delvefold.reset;

import com.nightsta69.delvefold.config.ConfigJson;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.TerrainVariant;
import com.nightsta69.delvefold.config.model.WorldSettingsDocument;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/** Creates and verifies tamper-evident manifests for Delvefold backups. */
public final class BackupManifestService {
    private static final long MAX_JSON_BYTES = 64L * 1024L * 1024L;
    private static final long MAX_CONFIG_BYTES = 4L * 1024L * 1024L;
    private static final Set<String> CONTROL_FILES = Set.of(
            BackupManifest.FILE_NAME,
            BackupVerificationReceipt.FILE_NAME,
            RestoreConfigSnapshot.COMPLETE_MARKER,
            ".pinned");

    private final Clock clock;

    public BackupManifestService() {
        this(Clock.systemUTC());
    }

    BackupManifestService(Clock clock) {
        this.clock = clock;
    }

    /**
     * Creates a manifest for a legacy or newly completed backup and immediately
     * verifies it. Call only from lifecycle or worker threads, never a server tick.
     */
    public Verification createVerifiedManifest(Path backupRoot) throws IOException {
        Path root = checkedRoot(backupRoot);
        if (Files.exists(root.resolve(BackupManifest.FILE_NAME))) {
            return verify(root);
        }
        PendingWorldOperation operation = validateRestorableLayout(root);
        List<BackupManifest.FileEntry> files = scanFiles(root);
        long totalBytes = totalBytes(files);
        BackupManifest manifest = new BackupManifest(
                BackupManifest.CURRENT_SCHEMA_VERSION,
                BackupManifest.HASH_ALGORITHM,
                metadata(root, operation),
                totalBytes,
                files);
        writeJsonAtomically(root.resolve(BackupManifest.FILE_NAME), manifest);
        return verify(root);
    }

    /** Performs a complete manifest/file comparison and persists a success receipt. */
    public Verification verify(Path backupRoot) throws IOException {
        Path root = checkedRoot(backupRoot);
        try {
            clearVerificationReceipt(root);
            PendingWorldOperation operation = validateRestorableLayout(root);
            BackupManifest manifest = readManifest(root);
            if (!metadata(root, operation).equals(manifest.metadata())) {
                throw new IOException("Backup manifest metadata does not match its operation marker");
            }
            List<BackupManifest.FileEntry> actual = scanFiles(root);
            if (!manifest.files().equals(actual)) {
                throw new IOException(describeMismatch(manifest.files(), actual));
            }
            long totalBytes = totalBytes(actual);
            if (manifest.totalBytes() != totalBytes) {
                throw new IOException("Backup manifest total size does not match its files");
            }
            String manifestHash = sha256(root.resolve(BackupManifest.FILE_NAME));
            Path manifestPath = root.resolve(BackupManifest.FILE_NAME);
            BackupVerificationReceipt receipt = new BackupVerificationReceipt(
                    BackupVerificationReceipt.CURRENT_SCHEMA_VERSION,
                    root.getFileName().toString(),
                    manifestHash,
                    Files.size(manifestPath),
                    Files.getLastModifiedTime(manifestPath).toMillis(),
                    clock.millis(),
                    actual.size(),
                    totalBytes);
            writeJsonAtomically(root.resolve(BackupVerificationReceipt.FILE_NAME), receipt);
            return new Verification(manifest, receipt);
        } catch (IOException | RuntimeException exception) {
            try {
                clearVerificationReceipt(root);
            } catch (IOException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            if (exception instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Backup verification failed", exception);
        }
    }

    /** Reads and structurally validates a manifest without hashing backup contents. */
    public BackupManifest readManifest(Path backupRoot) throws IOException {
        Path root = checkedRoot(backupRoot);
        Path path = root.resolve(BackupManifest.FILE_NAME);
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path) || Files.size(path) > MAX_JSON_BYTES) {
            throw new IOException("Backup manifest is missing or failed safety checks");
        }
        BackupManifest manifest;
        try {
            manifest = ConfigJson.GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), BackupManifest.class);
        } catch (RuntimeException exception) {
            throw new IOException("Backup manifest JSON is invalid", exception);
        }
        validateManifest(root, manifest);
        return manifest;
    }

    /**
     * Checks the small verification receipt against the manifest. This is safe
     * for listing screens; restoration still performs a complete verification.
     */
    public boolean hasCurrentVerification(Path backupRoot) {
        try {
            Path root = checkedRoot(backupRoot);
            BackupManifest manifest = readManifest(root);
            Path receiptPath = root.resolve(BackupVerificationReceipt.FILE_NAME);
            if (Files.isSymbolicLink(receiptPath) || !Files.isRegularFile(receiptPath)
                    || Files.size(receiptPath) > 64L * 1024L) {
                return false;
            }
            BackupVerificationReceipt receipt = ConfigJson.GSON.fromJson(
                    Files.readString(receiptPath, StandardCharsets.UTF_8), BackupVerificationReceipt.class);
            Path manifestPath = root.resolve(BackupManifest.FILE_NAME);
            if (receipt == null || !receipt.manifestSha256().matches("[0-9a-f]{64}")) {
                return false;
            }
            byte[] expectedHash = HexFormat.of().parseHex(receipt.manifestSha256());
            byte[] currentHash = HexFormat.of().parseHex(sha256(manifestPath));
            boolean manifestHashMatches = MessageDigest.isEqual(expectedHash, currentHash);
            return manifestHashMatches
                    && receipt.schemaVersion() == BackupVerificationReceipt.CURRENT_SCHEMA_VERSION
                    && receipt.backupId().equals(root.getFileName().toString())
                    && receipt.fileCount() == manifest.files().size()
                    && receipt.totalBytes() == manifest.totalBytes()
                    && receipt.manifestSizeBytes() == Files.size(manifestPath)
                    && receipt.manifestLastModifiedEpochMillis()
                    == Files.getLastModifiedTime(manifestPath).toMillis();
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    /** Validates the pre-manifest layout used by explicit legacy upgrades. */
    public void validateLegacyLayout(Path backupRoot) throws IOException {
        Path root = checkedRoot(backupRoot);
        validateRestorableLayout(root);
        scanFiles(root);
    }

    public static boolean hasManifest(Path backupRoot) {
        if (backupRoot == null) {
            return false;
        }
        Path path = backupRoot.toAbsolutePath().normalize().resolve(BackupManifest.FILE_NAME);
        return Files.isRegularFile(path) && !Files.isSymbolicLink(path);
    }

    private static Path checkedRoot(Path backupRoot) throws IOException {
        if (backupRoot == null) {
            throw new IOException("Backup path is missing");
        }
        Path root = backupRoot.toAbsolutePath().normalize();
        if (root.getFileName() == null || Files.isSymbolicLink(root) || !Files.isDirectory(root)) {
            throw new IOException("Backup root failed safety checks");
        }
        String id = root.getFileName().toString();
        if (!id.matches("[a-zA-Z0-9_.-]{1,200}")) {
            throw new IOException("Backup ID is invalid");
        }
        return root;
    }

    private static PendingWorldOperation validateRestorableLayout(Path root) throws IOException {
        Path marker = root.resolve("operation.json");
        if (Files.isSymbolicLink(marker) || !Files.isRegularFile(marker) || Files.size(marker) > 64L * 1024L) {
            throw new IOException("Backup operation marker failed safety checks");
        }
        PendingWorldOperation operation;
        try {
            operation = ConfigJson.GSON.fromJson(
                    Files.readString(marker, StandardCharsets.UTF_8), PendingWorldOperation.class);
        } catch (RuntimeException exception) {
            throw new IOException("Backup operation marker is invalid", exception);
        }
        if (operation == null || operation.schemaVersion() != PendingWorldOperation.CURRENT_SCHEMA_VERSION) {
            throw new IOException("Backup operation marker schema is unsupported");
        }
        try {
            UUID.fromString(operation.operationId());
        } catch (IllegalArgumentException exception) {
            throw new IOException("Backup operation ID is invalid", exception);
        }
        WorldSettingsDocument settings = readCurrentSchema(
                root.resolve("config/serverconfig/delvefold/settings.json"),
                WorldSettingsDocument.class, WorldSettingsDocument.CURRENT_SCHEMA_VERSION);
        OreProfileDocument ores = readCurrentSchema(
                root.resolve("config/serverconfig/delvefold/ores.json"),
                OreProfileDocument.class, OreProfileDocument.CURRENT_SCHEMA_VERSION);
        if (settings == null || ores == null) {
            throw new IOException("Backup configuration is missing or uses an unsupported schema");
        }
        validateDimensionSnapshot(root, operation, settings);
        return operation;
    }

    /**
     * Validates that a backup contains real data for its active canonical Delvefold dimension.
     * Older operation markers do not record the source variant, so an uninitialized settings
     * snapshot falls back to either canonical variant for the recorded source terrain.
     */
    static void validateDimensionSnapshot(
            Path root, PendingWorldOperation operation, WorldSettingsDocument settings) throws IOException {
        Path dimensions = root.resolve("dimensions/delvefold");
        if (Files.isSymbolicLink(dimensions) || !Files.isDirectory(dimensions)) {
            throw new IOException("Backup dimension snapshot is missing or unsafe");
        }

        List<Path> canonical = new ArrayList<>();
        try (Stream<Path> children = Files.list(dimensions)) {
            for (Path child : children.toList()) {
                String name = child.getFileName().toString();
                if (Files.isSymbolicLink(child) || !Files.isDirectory(child)
                        || !DelvefoldDimensionFolders.ALL_SET.contains(name)) {
                    throw new IOException("Backup contains an unknown top-level dimension entry: " + name);
                }
                canonical.add(child);
            }
        }

        String expected = expectedDimensionFolder(settings);
        if (expected != null) {
            Path active = dimensions.resolve(expected);
            if (!canonical.contains(active) || !containsRegularFile(active)) {
                throw new IOException("Backup is missing data for its active dimension: " + expected);
            }
            return;
        }

        TerrainMode sourceTerrain = operation == null ? null : operation.sourceTerrain();
        boolean found = canonical.stream()
                .filter(path -> sourceTerrain == null
                        || belongsToTerrain(path.getFileName().toString(), sourceTerrain))
                .anyMatch(BackupManifestService::containsRegularFileUnchecked);
        if (!found) {
            String suffix = sourceTerrain == null ? "" : " for source terrain " + sourceTerrain.serializedName();
            throw new IOException("Backup contains no canonical Delvefold dimension data" + suffix);
        }
    }

    private static String expectedDimensionFolder(WorldSettingsDocument settings) {
        if (settings == null || !settings.initialized() || settings.terrainMode() == null) {
            return null;
        }
        TerrainVariant variant = settings.identity().terrainVariant();
        return dimensionFolder(settings.terrainMode(), variant);
    }

    private static String dimensionFolder(TerrainMode terrain, TerrainVariant variant) {
        return "delve_" + terrain.serializedName()
                + (variant == TerrainVariant.EXPANSIVE ? "_expansive" : "");
    }

    private static boolean belongsToTerrain(String folder, TerrainMode terrain) {
        String classic = dimensionFolder(terrain, TerrainVariant.CLASSIC);
        return folder.equals(classic) || folder.equals(classic + "_expansive");
    }

    private static boolean containsRegularFileUnchecked(Path root) {
        try {
            return containsRegularFile(root);
        } catch (IOException exception) {
            return false;
        }
    }

    private static boolean containsRegularFile(Path root) throws IOException {
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root)) {
            return false;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.anyMatch(path -> !Files.isSymbolicLink(path) && Files.isRegularFile(path));
        }
    }

    static <T> T readCurrentSchema(Path path, Class<T> type, int expected) {
        try {
            if (Files.isSymbolicLink(path) || !Files.isRegularFile(path) || Files.size(path) > MAX_CONFIG_BYTES) {
                return null;
            }
            T value = ConfigJson.GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), type);
            if (value instanceof WorldSettingsDocument settings) {
                return settings.schemaVersion() == expected ? value : null;
            }
            if (value instanceof OreProfileDocument ores) {
                return ores.schemaVersion() == expected ? value : null;
            }
            return null;
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private static BackupManifest.Metadata metadata(Path root, PendingWorldOperation operation) {
        return new BackupManifest.Metadata(
                root.getFileName().toString(),
                operation.createdAtEpochMillis(),
                operation.operationId(),
                operation.type().name().toLowerCase(Locale.ROOT),
                operation.sourceTerrain() == null ? "unknown" : operation.sourceTerrain().serializedName(),
                operation.requestedBy());
    }

    private static List<BackupManifest.FileEntry> scanFiles(Path root) throws IOException {
        List<Path> regularFiles = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.toList()) {
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("Backup contains a symbolic link: " + relativePath(root, path));
                }
                if (Files.isDirectory(path)) {
                    continue;
                }
                if (!Files.isRegularFile(path)) {
                    throw new IOException("Backup contains a non-regular file: " + relativePath(root, path));
                }
                String relative = relativePath(root, path);
                if (!CONTROL_FILES.contains(relative)) {
                    regularFiles.add(path);
                }
            }
        }
        regularFiles.sort(Comparator.comparing(path -> relativePath(root, path)));
        List<BackupManifest.FileEntry> result = new ArrayList<>(regularFiles.size());
        for (Path path : regularFiles) {
            long before = Files.size(path);
            String hash = sha256(path);
            long after = Files.size(path);
            if (before != after) {
                throw new IOException("Backup file changed while it was being hashed: " + relativePath(root, path));
            }
            result.add(new BackupManifest.FileEntry(relativePath(root, path), after, hash));
        }
        return List.copyOf(result);
    }

    private static void validateManifest(Path root, BackupManifest manifest) throws IOException {
        if (manifest == null || manifest.schemaVersion() != BackupManifest.CURRENT_SCHEMA_VERSION) {
            throw new IOException("Backup manifest schema is unsupported");
        }
        if (!BackupManifest.HASH_ALGORITHM.equals(manifest.hashAlgorithm())) {
            throw new IOException("Backup manifest hash algorithm is unsupported");
        }
        if (manifest.metadata() == null
                || !manifest.metadata().backupId().equals(root.getFileName().toString())) {
            throw new IOException("Backup manifest metadata does not match its directory");
        }
        Set<String> unique = new HashSet<>();
        String previous = null;
        long total = 0L;
        for (BackupManifest.FileEntry entry : manifest.files()) {
            if (entry == null || entry.sizeBytes() < 0 || !entry.sha256().matches("[0-9a-f]{64}")) {
                throw new IOException("Backup manifest contains an invalid file entry");
            }
            String path = entry.path();
            if (!isNormalizedRelativePath(path) || CONTROL_FILES.contains(path) || !unique.add(path)) {
                throw new IOException("Backup manifest contains an unsafe or duplicate path: " + path);
            }
            if (previous != null && previous.compareTo(path) >= 0) {
                throw new IOException("Backup manifest file entries are not sorted");
            }
            Path resolved = root.resolve(path).normalize();
            if (!resolved.startsWith(root) || resolved.equals(root)) {
                throw new IOException("Backup manifest path escapes its directory: " + path);
            }
            previous = path;
            total = safeAdd(total, entry.sizeBytes());
        }
        if (manifest.totalBytes() != total) {
            throw new IOException("Backup manifest total size is inconsistent");
        }
    }

    private static boolean isNormalizedRelativePath(String value) {
        if (value == null || value.isBlank() || value.startsWith("/") || value.contains("\\")) {
            return false;
        }
        try {
            Path path = Path.of(value);
            return !path.isAbsolute()
                    && path.getNameCount() > 0
                    && path.normalize().toString().replace(path.getFileSystem().getSeparator(), "/").equals(value)
                    && !containsDotSegment(path);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean containsDotSegment(Path path) {
        for (Path part : path) {
            if (part.toString().equals(".") || part.toString().equals("..")) {
                return true;
            }
        }
        return false;
    }

    private static String describeMismatch(
            List<BackupManifest.FileEntry> expected,
            List<BackupManifest.FileEntry> actual) {
        Set<String> expectedPaths = expected.stream().map(BackupManifest.FileEntry::path)
                .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
        Set<String> actualPaths = actual.stream().map(BackupManifest.FileEntry::path)
                .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
        Set<String> missing = new java.util.TreeSet<>(expectedPaths);
        missing.removeAll(actualPaths);
        Set<String> unexpected = new java.util.TreeSet<>(actualPaths);
        unexpected.removeAll(expectedPaths);
        if (!missing.isEmpty()) {
            return "Backup is missing a manifest file: " + missing.iterator().next();
        }
        if (!unexpected.isEmpty()) {
            return "Backup contains a file not present in its manifest: " + unexpected.iterator().next();
        }
        for (int index = 0; index < Math.min(expected.size(), actual.size()); index++) {
            BackupManifest.FileEntry left = expected.get(index);
            BackupManifest.FileEntry right = actual.get(index);
            if (!left.equals(right)) {
                return "Backup file failed size or SHA-256 verification: " + left.path();
            }
        }
        return "Backup contents do not match the manifest";
    }

    private static long totalBytes(List<BackupManifest.FileEntry> files) throws IOException {
        long total = 0L;
        for (BackupManifest.FileEntry entry : files) {
            total = safeAdd(total, entry.sizeBytes());
        }
        return total;
    }

    private static long safeAdd(long left, long right) throws IOException {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw new IOException("Backup size exceeds the supported range", exception);
        }
    }

    private static String relativePath(Path root, Path path) {
        return root.relativize(path).toString().replace(path.getFileSystem().getSeparator(), "/");
    }

    private static String sha256(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(BackupManifest.HASH_ALGORITHM);
        } catch (GeneralSecurityException exception) {
            throw new IOException("SHA-256 is unavailable", exception);
        }
        byte[] buffer = new byte[64 * 1024];
        try (InputStream input = Files.newInputStream(path)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void clearVerificationReceipt(Path root) throws IOException {
        Files.deleteIfExists(root.resolve(BackupVerificationReceipt.FILE_NAME));
    }

    private static void writeJsonAtomically(Path target, Object value) throws IOException {
        byte[] bytes = (ConfigJson.GSON.toJson(value) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        boolean moved = false;
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    public record Verification(BackupManifest manifest, BackupVerificationReceipt receipt) {
    }
}
