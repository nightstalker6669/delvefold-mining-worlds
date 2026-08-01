package com.nightsta69.delvefold.config;

import com.google.gson.JsonElement;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.validation.OreConfigValidator;
import com.nightsta69.delvefold.config.validation.RegistryLookup;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Thread-safe registry of read-only ore profiles supplied by datapacks or optional script integrations.
 *
 * <p>Datapacks are atomically replaced at server resource reload. Script registrations live for the game process and
 * override a same-ID datapack entry in merged views. Documents are immutable schema-2 snapshots; neither source can be
 * edited by Delvefold's profile GUI. Registry-dependent validation still occurs when an administrator activates a
 * profile because script parsing intentionally has no live Minecraft registry dependency.
 */
public final class EcosystemProfileRegistry {
    /** Maximum UTF-8 encoded size of one datapack or script profile: 256 KiB. */
    public static final int MAX_PROFILE_BYTES = 256 * 1024;

    private static final System.Logger LOGGER = System.getLogger(EcosystemProfileRegistry.class.getName());
    private static final Pattern PROFILE_ID = Pattern.compile("(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+");
    private static final AtomicReference<Map<String, RegisteredProfile>> DATAPACKS = new AtomicReference<>(Map.of());
    private static final Map<String, RegisteredProfile> SCRIPTS = new ConcurrentHashMap<>();

    private EcosystemProfileRegistry() {}

    /**
     * Returns a point-in-time immutable merged profile snapshot.
     *
     * <p>Datapack iteration order is retained, scripted IDs are applied in lexical order, and a scripted profile wins
     * when both sources use the same ID. Later registry mutations do not change the returned map.
     *
     * @return unmodifiable profile map keyed by local or namespaced profile ID
     */
    public static Map<String, RegisteredProfile> profiles() {
        Map<String, RegisteredProfile> merged =
                new LinkedHashMap<>(java.util.Objects.requireNonNull(DATAPACKS.get(), "datapack profiles"));
        SCRIPTS.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> merged.put(entry.getKey(), entry.getValue()));
        return Map.copyOf(merged);
    }

    /**
     * Finds the current profile for an exact ID, preferring a script registration over a datapack registration.
     *
     * @param id normalized local or namespaced profile ID
     * @return immutable registered-profile value, or {@code null} when no current source provides the ID
     */
    public static @Nullable RegisteredProfile find(String id) {
        RegisteredProfile scripted = SCRIPTS.get(id);
        return scripted == null
                ? java.util.Objects.requireNonNull(DATAPACKS.get(), "datapack profiles")
                        .get(id)
                : scripted;
    }

    /**
     * Registers strict schema-2 JSON through the trusted script bridge.
     *
     * <p>The UTF-8 payload must contain 1 through {@value #MAX_PROFILE_BYTES} bytes, the owner is limited to 64 safe
     * lowercase characters, and the profile ID must be namespaced. Parsing rejects unknown structure and validation
     * applies all non-registry profile limits. A successful call atomically replaces any scripted entry with the same
     * ID, remains active for this game process, and is exposed read-only in Delvefold's GUI.
     *
     * @param owner lowercase script integration owner used in provenance and unregister ownership checks
     * @param id namespaced profile ID such as {@code packname:rich_ores}
     * @param json complete schema-2 ore-profile JSON
     * @throws IllegalArgumentException if owner, ID, size, JSON structure, schema, or profile validation is invalid
     */
    public static void registerScriptJson(String owner, String id, String json) {
        String safeOwner = validateOwner(owner);
        String safeId = validateNamespacedProfileId(id);
        OreProfileDocument document = parseProfile(safeId, json);
        SCRIPTS.put(safeId, new RegisteredProfile(document, "script:" + safeOwner));
        LOGGER.log(System.Logger.Level.INFO, "Registered Delvefold script profile {0} from {1}", safeId, safeOwner);
    }

    /**
     * Removes a scripted profile only when its recorded owner matches the supplied owner.
     *
     * @param owner lowercase script owner expected to own the registration
     * @param id namespaced scripted profile ID
     * @return {@code true} when the owner-matched entry was atomically removed
     * @throws IllegalArgumentException if owner or ID syntax is invalid
     */
    public static boolean unregisterScriptProfile(String owner, String id) {
        String safeOwner = validateOwner(owner);
        String safeId = validateNamespacedProfileId(id);
        RegisteredProfile existing = SCRIPTS.get(safeId);
        return existing != null && existing.source().equals("script:" + safeOwner) && SCRIPTS.remove(safeId, existing);
    }

    /**
     * Normalizes and validates a local or namespaced ecosystem profile ID.
     *
     * @param id profile ID to trim and validate
     * @return normalized ID containing at most {@link OreProfileCatalog#MAX_PROFILE_ID_LENGTH} lowercase characters
     * @throws IllegalArgumentException if the ID is blank, too long, or contains unsupported characters
     */
    public static String validateProfileId(String id) {
        String normalized = id == null ? "" : id.trim();
        if (normalized.length() > OreProfileCatalog.MAX_PROFILE_ID_LENGTH
                || !PROFILE_ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Profile ID must be a lowercase path, optionally namespaced (example: packname:rich_ores)");
        }
        return normalized;
    }

    private static String validateNamespacedProfileId(String id) {
        String normalized = validateProfileId(id);
        if (!normalized.contains(":")) {
            throw new IllegalArgumentException(
                    "Ecosystem profile IDs must be namespaced (example: packname:rich_ores)");
        }
        return normalized;
    }

    private static String validateOwner(String owner) {
        String normalized = owner == null ? "" : owner.trim();
        if (!normalized.matches("[a-z0-9_.-]{1,64}")) {
            throw new IllegalArgumentException("Script owner must use lowercase letters, digits, _, . or -");
        }
        return normalized;
    }

    private static OreProfileDocument parseProfile(String id, String json) {
        byte[] bytes = (json == null ? "" : json).getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0 || bytes.length > MAX_PROFILE_BYTES) {
            throw new IllegalArgumentException("Profile JSON must be between 1 and " + MAX_PROFILE_BYTES + " bytes");
        }
        try {
            JsonElement parsed = StrictConfigStructure.parseAndValidate(json, OreProfileDocument.class);
            OreProfileDocument decoded = ConfigJson.GSON.fromJson(parsed, OreProfileDocument.class);
            OreProfileDocument normalized =
                    new OreProfileDocument(OreProfileDocument.CURRENT_SCHEMA_VERSION, 0, id, decoded.rules());
            var validation = OreConfigValidator.validate(normalized, RegistryLookup.SKIP);
            if (!validation.valid()) {
                throw new IllegalArgumentException("Profile validation failed: " + validation.issues());
            }
            return normalized;
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException argument) {
                throw argument;
            }
            throw new IllegalArgumentException("Invalid profile JSON: " + exception.getMessage(), exception);
        }
    }

    static OreProfileDocument parseDatapackProfile(String id, String json) {
        return parseProfile(validateProfileId(id), json);
    }

    static void replaceDatapackProfiles(Map<String, RegisteredProfile> profiles) {
        DATAPACKS.set(Map.copyOf(profiles));
    }

    /**
     * Immutable ecosystem profile plus redacted provenance label.
     *
     * @param document immutable normalized schema-2 document at revision zero
     * @param source source label such as a datapack pack ID or validated script owner
     */
    public record RegisteredProfile(OreProfileDocument document, String source) {
        /**
         * Creates a registration, requiring a document and normalizing a blank source to {@code unknown}.
         *
         * @param document immutable normalized profile document
         * @param source provenance label
         * @throws NullPointerException if {@code document} is {@code null}
         */
        public RegisteredProfile {
            java.util.Objects.requireNonNull(document, "document");
            source = source == null || source.isBlank() ? "unknown" : source;
        }
    }
}
