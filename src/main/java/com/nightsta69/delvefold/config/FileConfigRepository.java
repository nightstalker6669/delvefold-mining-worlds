package com.nightsta69.delvefold.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.WorldSettingsDocument;
import com.nightsta69.delvefold.config.validation.ConfigIssue;
import com.nightsta69.delvefold.config.validation.OreConfigValidator;
import com.nightsta69.delvefold.config.validation.RegistryLookup;
import com.nightsta69.delvefold.config.validation.ValidationReport;
import com.nightsta69.delvefold.config.validation.WorldSettingsValidator;
import com.nightsta69.delvefold.internal.io.AtomicFiles;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Save-local transactional repository for canonical {@code ores.json} and {@code settings.json}.
 *
 * <p>The repository enforces strict structure, schema 2, registry and cross-file validation, regular-file/symlink
 * safety, and bounded UTF-8 payloads. Writes first persist a bounded transaction record, then replace each canonical
 * file through a forced temporary file and atomic move where supported; an interrupted transaction is validated and
 * recovered by the next mutating load. The repository is not internally synchronized, so callers must serialize loads
 * and saves for one path set.
 */
public final class FileConfigRepository {
    /** Maximum UTF-8 or on-disk size of each canonical configuration file: 4 MiB. */
    public static final long MAX_CONFIG_BYTES = 4L * 1024L * 1024L;

    private static final long MAX_TRANSACTION_BYTES = 2L * MAX_CONFIG_BYTES + 64L * 1024L;
    private static final int TRANSACTION_SCHEMA_VERSION = 1;

    private final ConfigPaths paths;
    private final RegistryLookup registryLookup;

    /**
     * Creates a repository bound to one save and one registry-validation view.
     *
     * @param paths normalized save-local canonical configuration paths
     * @param registryLookup read-only registry lookup used for ore target validation
     */
    public FileConfigRepository(ConfigPaths paths, RegistryLookup registryLookup) {
        this.paths = paths;
        this.registryLookup = registryLookup;
    }

    /**
     * Recovers an interrupted transaction, creates missing default files, and loads a validated configuration snapshot.
     *
     * <p>Unsupported schema, malformed JSON, unsafe existing files, or validation failure never overwrite the rejected
     * canonical files. The result instead returns {@code lastGood}, or immutable built-in defaults when none exists,
     * with {@link ConfigLoadResult#usedFallback()} set. This method mutates disk only for directory/default creation
     * and validated pending-transaction recovery; it does not publish the returned snapshot itself.
     *
     * @param lastGood previously published immutable snapshot, or {@code null} during first startup
     * @return accepted disk snapshot or an explicitly diagnosed fallback snapshot
     * @throws IOException if directory/default creation or validated transaction recovery cannot complete safely
     */
    public ConfigLoadResult loadOrCreate(@Nullable ConfigSnapshot lastGood) throws IOException {
        Files.createDirectories(paths.directory());
        recoverPendingTransaction();
        if (Files.notExists(paths.ores())) {
            writeJsonAtomically(paths.ores(), OrePresets.create(OrePreset.VANILLA_BALANCED));
        }
        if (Files.notExists(paths.settings())) {
            writeJsonAtomically(paths.settings(), WorldSettingsDocument.uninitialized());
        }

        List<ConfigIssue> issues = new ArrayList<>();
        try {
            ensureSafeRegularFile(paths.ores());
            ensureSafeRegularFile(paths.settings());
            if (!supportedSchemas(paths.ores(), paths.settings(), issues)) {
                return fallback(lastGood, issues, "Configuration schema is incompatible");
            }
            OreProfileDocument ores = readJson(paths.ores(), OreProfileDocument.class);
            WorldSettingsDocument settings = readJson(paths.settings(), WorldSettingsDocument.class);
            ValidationReport report = OreConfigValidator.validate(ores, registryLookup);
            issues.addAll(report.issues());
            ValidationReport settingsReport = WorldSettingsValidator.validate(settings);
            issues.addAll(settingsReport.issues());
            ValidationReport consistencyReport = profileConsistency(ores, settings);
            issues.addAll(consistencyReport.issues());
            if (!report.valid() || !settingsReport.valid() || !consistencyReport.valid()) {
                return fallback(lastGood, issues, "Ore configuration did not pass validation");
            }
            String hash = hash(paths.ores(), paths.settings());
            ConfigSnapshot snapshot = new ConfigSnapshot(
                    ores, settings, combined(combined(report, settingsReport), consistencyReport), Instant.now(), hash);
            return new ConfigLoadResult(snapshot, false, issues);
        } catch (IOException | RuntimeException exception) {
            issues.add(ConfigIssue.error("json.invalid", "$", exception.getMessage()));
            return fallback(lastGood, issues, "Configuration JSON could not be parsed");
        }
    }

    /**
     * Validates and transactionally persists both canonical documents as one logical configuration revision.
     *
     * <p>If replacement fails after the transaction record is forced to disk, that record remains for deterministic
     * recovery on the next mutating load. The returned hash is SHA-256 over the final ore bytes followed by settings
     * bytes. Each canonical document is limited to {@value #MAX_CONFIG_BYTES} bytes.
     *
     * @param ores complete immutable ore-profile candidate
     * @param settings complete immutable world-settings candidate with a matching active profile ID
     * @return validated immutable snapshot reflecting the committed disk bytes
     * @throws IllegalArgumentException if either document or their cross-file profile identity is invalid
     * @throws IOException if a transaction is already pending or bounded transactional persistence fails
     */
    public ConfigSnapshot save(OreProfileDocument ores, WorldSettingsDocument settings) throws IOException {
        ValidationReport report = OreConfigValidator.validate(ores, registryLookup);
        ValidationReport settingsReport = WorldSettingsValidator.validate(settings);
        ValidationReport consistencyReport = profileConsistency(ores, settings);
        if (!report.valid() || !settingsReport.valid() || !consistencyReport.valid()) {
            throw new IllegalArgumentException("Refusing to save invalid Delvefold configuration");
        }
        Files.createDirectories(paths.directory());
        Path transactionPath = transactionPath();
        if (Files.exists(transactionPath)) {
            throw new IOException(
                    "A previous configuration transaction must be recovered by reloading or restarting first");
        }
        writeJsonAtomically(
                transactionPath,
                new ConfigTransaction(TRANSACTION_SCHEMA_VERSION, ores, settings),
                MAX_TRANSACTION_BYTES);
        writeJsonAtomically(paths.ores(), ores);
        writeJsonAtomically(paths.settings(), settings);
        Files.delete(transactionPath);
        return new ConfigSnapshot(
                ores,
                settings,
                combined(combined(report, settingsReport), consistencyReport),
                Instant.now(),
                hash(paths.ores(), paths.settings()));
    }

    /**
     * Reads and validates the current files without creating, recovering, replacing, or publishing anything.
     *
     * <p>A pending transaction is reported rather than recovered. Missing, unsafe, oversized, incompatible, malformed,
     * or invalid files return {@code lastGood} or built-in defaults as a diagnosed fallback, leaving disk untouched.
     *
     * @param lastGood previously published immutable snapshot, or {@code null} when none exists
     * @return accepted read-only candidate or an explicitly diagnosed fallback snapshot
     * @throws IOException if an unrecoverable low-level validation read fails outside normal fallback containment
     */
    public ConfigLoadResult validateDisk(@Nullable ConfigSnapshot lastGood) throws IOException {
        List<ConfigIssue> issues = new ArrayList<>();
        try {
            if (Files.exists(transactionPath())) {
                issues.add(ConfigIssue.error(
                        "transaction.pending",
                        "$",
                        "A configuration transaction is pending recovery; reload or restart before validating"));
                return fallback(lastGood, issues, "Configuration transaction recovery is pending");
            }
            if (Files.notExists(paths.ores()) || Files.notExists(paths.settings())) {
                issues.add(ConfigIssue.error("config.missing", "$", "Both ores.json and settings.json must exist"));
                return fallback(lastGood, issues, "Configuration files are missing");
            }
            ensureSafeRegularFile(paths.ores());
            ensureSafeRegularFile(paths.settings());
            if (!supportedSchemas(paths.ores(), paths.settings(), issues)) {
                return fallback(lastGood, issues, "Configuration schema is incompatible");
            }
            OreProfileDocument ores = readJson(paths.ores(), OreProfileDocument.class);
            WorldSettingsDocument settings = readJson(paths.settings(), WorldSettingsDocument.class);
            ValidationReport oresReport = OreConfigValidator.validate(ores, registryLookup);
            ValidationReport settingsReport = WorldSettingsValidator.validate(settings);
            ValidationReport consistencyReport = profileConsistency(ores, settings);
            issues.addAll(oresReport.issues());
            issues.addAll(settingsReport.issues());
            issues.addAll(consistencyReport.issues());
            if (!oresReport.valid() || !settingsReport.valid() || !consistencyReport.valid()) {
                return fallback(lastGood, issues, "Configuration files did not pass validation");
            }
            ConfigSnapshot candidate = new ConfigSnapshot(
                    ores,
                    settings,
                    combined(combined(oresReport, settingsReport), consistencyReport),
                    Instant.now(),
                    hash(paths.ores(), paths.settings()));
            return new ConfigLoadResult(candidate, false, issues);
        } catch (IOException | RuntimeException exception) {
            issues.add(ConfigIssue.error("json.invalid", "$", exception.getMessage()));
            return fallback(lastGood, issues, "Configuration files could not be validated");
        }
    }

    /**
     * Returns the immutable normalized paths to which this repository is bound.
     *
     * @return canonical save-local path set
     */
    public ConfigPaths paths() {
        return paths;
    }

    private ConfigLoadResult fallback(@Nullable ConfigSnapshot lastGood, List<ConfigIssue> issues, String reason)
            throws IOException {
        if (lastGood != null) {
            issues.add(ConfigIssue.warning(
                    "fallback.last_good", "$", reason + "; continuing with the last known-good snapshot"));
            return new ConfigLoadResult(lastGood, true, issues);
        }
        OreProfileDocument defaultOres = OrePresets.create(OrePreset.VANILLA_BALANCED);
        WorldSettingsDocument defaultSettings = WorldSettingsDocument.uninitialized();
        ValidationReport report = OreConfigValidator.validate(defaultOres, registryLookup);
        ConfigSnapshot defaults = new ConfigSnapshot(
                defaultOres,
                defaultSettings,
                combined(report, WorldSettingsValidator.validate(defaultSettings)),
                Instant.now(),
                "built-in-defaults");
        issues.add(ConfigIssue.warning(
                "fallback.defaults", "$", reason + "; using built-in defaults without overwriting the rejected files"));
        return new ConfigLoadResult(defaults, true, issues);
    }

    private static <T> T readJson(Path path, Class<T> type) throws IOException {
        return readJson(path, type, MAX_CONFIG_BYTES);
    }

    private static boolean supportedSchemas(Path ores, Path settings, List<ConfigIssue> issues) throws IOException {
        int oreSchema = schemaVersion(ores);
        int settingsSchema = schemaVersion(settings);
        if (oreSchema != OreProfileDocument.CURRENT_SCHEMA_VERSION) {
            issues.add(ConfigIssue.error(
                    "schema.unsupported",
                    "$.schema_version",
                    "Expected ore schema " + OreProfileDocument.CURRENT_SCHEMA_VERSION + " but found " + oreSchema));
        }
        if (settingsSchema != WorldSettingsDocument.CURRENT_SCHEMA_VERSION) {
            issues.add(ConfigIssue.error(
                    "settings.schema.unsupported",
                    "$.schema_version",
                    "Expected settings schema " + WorldSettingsDocument.CURRENT_SCHEMA_VERSION + " but found "
                            + settingsSchema));
        }
        return oreSchema == OreProfileDocument.CURRENT_SCHEMA_VERSION
                && settingsSchema == WorldSettingsDocument.CURRENT_SCHEMA_VERSION;
    }

    private static int schemaVersion(Path path) throws IOException {
        if (Files.size(path) > MAX_CONFIG_BYTES) {
            throw new IOException(path.getFileName() + " exceeds the configuration size limit");
        }
        try {
            JsonElement root = com.google.gson.JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
            if (!root.isJsonObject()
                    || !root.getAsJsonObject().has("schema_version")
                    || !root.getAsJsonObject().get("schema_version").isJsonPrimitive()
                    || !root.getAsJsonObject()
                            .get("schema_version")
                            .getAsJsonPrimitive()
                            .isNumber()) {
                throw new IOException(path.getFileName() + " has no numeric schema_version");
            }
            BigInteger value = root.getAsJsonObject()
                    .get("schema_version")
                    .getAsBigDecimal()
                    .toBigIntegerExact();
            if (value.compareTo(BigInteger.ZERO) < 0 || value.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
                throw new IOException(path.getFileName() + " schema_version is outside the supported integer range");
            }
            return value.intValueExact();
        } catch (RuntimeException exception) {
            throw new IOException(path.getFileName() + " schema could not be read", exception);
        }
    }

    private static <T> T readJson(Path path, Class<T> type, long maximumBytes) throws IOException {
        long size = Files.size(path);
        if (size > maximumBytes) {
            throw new IOException(path.getFileName() + " exceeds the " + maximumBytes + " byte safety limit");
        }
        String json = Files.readString(path, StandardCharsets.UTF_8);
        JsonElement parsed = StrictConfigStructure.parseAndValidate(json, type);
        T value = ConfigJson.GSON.fromJson(parsed, type);
        if (value == null) {
            throw new JsonParseException(path.getFileName() + " contains JSON null");
        }
        return value;
    }

    private static void ensureSafeRegularFile(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path)) {
            throw new IOException("Refusing to read non-regular configuration file: " + path);
        }
    }

    private static void writeJsonAtomically(Path target, Object value) throws IOException {
        writeJsonAtomically(target, value, MAX_CONFIG_BYTES);
    }

    private static void writeJsonAtomically(Path target, Object value, long maximumBytes) throws IOException {
        byte[] bytes = (ConfigJson.GSON.toJson(value) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maximumBytes) {
            throw new IOException(target.getFileName() + " would exceed the " + maximumBytes + " byte safety limit");
        }
        AtomicFiles.writeReplacing(target, bytes);
    }

    private static String hash(Path... paths) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Path path : paths) {
                digest.update(Files.readAllBytes(path));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private void recoverPendingTransaction() throws IOException {
        Path transactionPath = transactionPath();
        if (Files.notExists(transactionPath)) {
            return;
        }
        ensureSafeRegularFile(transactionPath);
        ConfigTransaction transaction = readJson(transactionPath, ConfigTransaction.class, MAX_TRANSACTION_BYTES);
        if (transaction.schemaVersion() != TRANSACTION_SCHEMA_VERSION
                || transaction.ores() == null
                || transaction.settings() == null) {
            throw new IOException("Configuration transaction has an unsupported or incomplete schema");
        }
        ValidationReport oresReport = OreConfigValidator.validate(transaction.ores(), registryLookup);
        ValidationReport settingsReport = WorldSettingsValidator.validate(transaction.settings());
        ValidationReport consistencyReport = profileConsistency(transaction.ores(), transaction.settings());
        if (!oresReport.valid() || !settingsReport.valid() || !consistencyReport.valid()) {
            throw new IOException("Configuration transaction failed validation and was not applied");
        }
        writeJsonAtomically(paths.ores(), transaction.ores());
        writeJsonAtomically(paths.settings(), transaction.settings());
        Files.delete(transactionPath);
    }

    private Path transactionPath() {
        return paths.directory().resolve("config_transaction.json");
    }

    private static ValidationReport combined(ValidationReport first, ValidationReport second) {
        List<ConfigIssue> issues =
                new ArrayList<>(first.issues().size() + second.issues().size());
        issues.addAll(first.issues());
        issues.addAll(second.issues());
        return new ValidationReport(issues);
    }

    private static ValidationReport profileConsistency(OreProfileDocument ores, WorldSettingsDocument settings) {
        if (ores.profile().equals(settings.activeProfileId())) {
            return new ValidationReport(List.of());
        }
        return new ValidationReport(List.of(ConfigIssue.error(
                "profile.active_mismatch",
                "$.active_profile_id",
                "settings active_profile_id '" + settings.activeProfileId()
                        + "' does not match the active ore profile '" + ores.profile() + "'")));
    }

    private record ConfigTransaction(int schemaVersion, OreProfileDocument ores, WorldSettingsDocument settings) {}
}
