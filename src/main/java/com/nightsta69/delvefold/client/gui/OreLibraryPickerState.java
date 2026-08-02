package com.nightsta69.delvefold.client.gui;

import com.nightsta69.delvefold.admin.AdminLocalizedMessage;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.network.ProtocolLimits;
import com.nightsta69.delvefold.network.model.AdminSnapshot;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Local, non-authoritative interaction state for the material-family ore picker.
 *
 * <p>The server remains responsible for discovery bounds, permissions, revision checks, collision-safe rule IDs, and
 * committing the complete batch atomically. This state only retains page-independent client selection and converts a
 * server-described family into an editable ore-rule draft.
 */
final class OreLibraryPickerState {
    static final int MAX_SELECTED_FAMILIES = ProtocolLimits.MAX_IMPORT_SELECTED_GROUPS;
    private final Map<String, Family> families = new TreeMap<>();
    private final Set<String> selectedFamilyIds = new LinkedHashSet<>();
    private final Map<String, LinkedHashSet<String>> selectedCandidateIds = new LinkedHashMap<>();
    private final Set<String> currentPageFamilyIds = new LinkedHashSet<>();
    private String catalogToken = "";
    private String searchQuery = "";
    private boolean showConfigured;
    private int page;

    /** Replaces the locally known catalog while retaining compatible choices from the previous view. */
    void replaceFamilies(Collection<Family> replacements) {
        Objects.requireNonNull(replacements, "replacements");
        Map<String, LinkedHashSet<String>> previousCandidates = new LinkedHashMap<>(this.selectedCandidateIds);
        this.families.clear();
        this.currentPageFamilyIds.clear();
        for (Family family : replacements) {
            Family previous = this.families.putIfAbsent(family.id(), family);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate ore family ID: " + family.id());
            }
            this.currentPageFamilyIds.add(family.id());
        }

        this.selectedFamilyIds.retainAll(this.families.keySet());
        this.selectedCandidateIds.clear();
        for (Family family : this.families.values()) {
            Set<String> valid =
                    family.candidates().stream().map(Candidate::blockId).collect(Collectors.toSet());
            LinkedHashSet<String> retained = previousCandidates.get(family.id());
            if (retained != null) {
                retained.retainAll(valid);
            }
            this.selectedCandidateIds.put(
                    family.id(),
                    retained == null || retained.isEmpty() ? new LinkedHashSet<>(defaultCandidates(family)) : retained);
        }
        this.selectedFamilyIds.removeIf(id -> selectedCandidates(id).isEmpty());
    }

    /**
     * Accepts one authoritative page without discarding selections made on other pages of the same catalog.
     *
     * <p>A changed server token denotes a different registry/revision snapshot and therefore clears selections that
     * could no longer be safely submitted.
     */
    void acceptPage(String newCatalogToken, Collection<Family> replacements) {
        acceptPage(newCatalogToken, replacements, false);
    }

    /**
     * Accepts one authoritative page and optionally retains selections across a safely renewed catalog token.
     *
     * <p>Selection retention is valid only for an expired or missing session whose expected ore revision remains
     * accepted by the server. Registry and base-profile invalidations must use {@code false} so stale candidates are
     * discarded.
     */
    void acceptPage(String newCatalogToken, Collection<Family> replacements, boolean retainSelectionsWhenTokenChanges) {
        String normalizedToken =
                Objects.requireNonNull(newCatalogToken, "newCatalogToken").trim();
        if (normalizedToken.isEmpty()) {
            throw new IllegalArgumentException("catalog token must not be blank");
        }
        if (!retainSelectionsWhenTokenChanges
                && !this.catalogToken.isEmpty()
                && !this.catalogToken.equals(normalizedToken)) {
            this.families.clear();
            this.selectedFamilyIds.clear();
            this.selectedCandidateIds.clear();
        }
        this.catalogToken = normalizedToken;
        this.currentPageFamilyIds.clear();
        for (Family family : Objects.requireNonNull(replacements, "replacements")) {
            this.currentPageFamilyIds.add(family.id());
            Family previous = this.families.put(family.id(), family);
            LinkedHashSet<String> retained = this.selectedCandidateIds.get(family.id());
            Set<String> valid =
                    family.candidates().stream().map(Candidate::blockId).collect(Collectors.toSet());
            if (retained != null) {
                retained.retainAll(valid);
            }
            if (previous == null || retained == null || retained.isEmpty()) {
                this.selectedCandidateIds.put(family.id(), new LinkedHashSet<>(defaultCandidates(family)));
            }
            if (family.configured()) {
                this.selectedFamilyIds.remove(family.id());
            }
        }
    }

    /** Returns every known family after applying configured and search filters. */
    List<Family> filteredFamilies() {
        String query = this.searchQuery.trim().toLowerCase(Locale.ROOT);
        return this.families.values().stream()
                .filter(family -> this.showConfigured || !family.configured())
                .filter(family -> query.isEmpty() || matches(family, query))
                .toList();
    }

    /** Returns the current server page in authoritative order. */
    List<Family> pageFamilies() {
        return this.currentPageFamilyIds.stream()
                .map(this.families::get)
                .filter(Objects::nonNull)
                .toList();
    }

    /** Selects or deselects one family without discarding its candidate choices. */
    boolean toggleFamily(String familyId) {
        Family family = this.families.get(familyId);
        if (family == null
                || family.configured()
                || selectedCandidates(familyId).isEmpty()) {
            return false;
        }
        if (this.selectedFamilyIds.remove(familyId)) {
            return true;
        }
        if (this.selectedFamilyIds.size() >= MAX_SELECTED_FAMILIES) {
            return false;
        }
        this.selectedFamilyIds.add(familyId);
        return true;
    }

    /** Adds every currently visible, unconfigured family up to the bounded batch limit. */
    int selectVisible(Collection<Family> visibleFamilies) {
        for (Family family : visibleFamilies) {
            if (this.selectedFamilyIds.size() >= MAX_SELECTED_FAMILIES) {
                break;
            }
            if (!family.configured() && !selectedCandidates(family.id()).isEmpty()) {
                this.selectedFamilyIds.add(family.id());
            }
        }
        return this.selectedFamilyIds.size();
    }

    /** Clears family selection while preserving per-candidate choices for later review. */
    void clearSelection() {
        this.selectedFamilyIds.clear();
    }

    /** Enables or disables one exact provider/host candidate in a family. */
    boolean toggleCandidate(String familyId, String blockId) {
        Family family = this.families.get(familyId);
        if (family == null
                || family.configured()
                || family.candidates().stream().noneMatch(c -> c.blockId().equals(blockId))) {
            return false;
        }
        LinkedHashSet<String> selected =
                this.selectedCandidateIds.computeIfAbsent(familyId, ignored -> new LinkedHashSet<>());
        if (!selected.remove(blockId)) {
            if (selected.size() >= ProtocolLimits.MAX_VARIANTS) {
                return false;
            }
            selected.add(blockId);
        }
        if (selected.isEmpty()) {
            this.selectedFamilyIds.remove(familyId);
        }
        return true;
    }

    /** Selects every candidate supplied by one provider and disables the family's other providers. */
    boolean selectProvider(String familyId, String providerNamespace) {
        Family family = this.families.get(familyId);
        if (family == null || family.configured()) {
            return false;
        }
        LinkedHashSet<String> selected = family.candidates().stream()
                .filter(candidate ->
                        candidate.providerNamespace().equals(providerNamespace) && !candidate.reviewRequired())
                .map(Candidate::blockId)
                .limit(ProtocolLimits.MAX_VARIANTS)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (selected.isEmpty()) {
            return false;
        }
        this.selectedCandidateIds.put(familyId, selected);
        return true;
    }

    /** Returns whether one family is selected for the next atomic batch. */
    boolean selected(String familyId) {
        return this.selectedFamilyIds.contains(familyId);
    }

    /** Returns whether one exact candidate is enabled in its family draft. */
    boolean candidateSelected(String familyId, String blockId) {
        return selectedCandidates(familyId).contains(blockId);
    }

    /** Returns the number of selected material families. */
    int selectedCount() {
        return this.selectedFamilyIds.size();
    }

    /** Returns selected server-issued family IDs in deterministic request order. */
    List<String> selectedFamilyIds() {
        return this.selectedFamilyIds.stream().sorted().toList();
    }

    /** Returns one known family or {@code null} when a replacement page no longer contains it. */
    @Nullable Family family(String familyId) {
        return this.families.get(familyId);
    }

    /** Builds deterministic, immutable drafts for the complete selected family batch. */
    List<AdminSnapshot.OreRuleDraft> selectedDrafts() {
        List<AdminSnapshot.OreRuleDraft> drafts = new ArrayList<>();
        for (String familyId : this.selectedFamilyIds.stream().sorted().toList()) {
            Family family = this.families.get(familyId);
            if (family == null || family.configured()) {
                continue;
            }
            Set<String> selected = selectedCandidates(familyId);
            List<Candidate> candidates = family.candidates().stream()
                    .filter(candidate -> selected.contains(candidate.blockId()))
                    .toList();
            if (!candidates.isEmpty()) {
                drafts.add(toDraft(family, candidates));
            }
        }
        return List.copyOf(drafts);
    }

    /** Builds one family draft using its retained provider/host candidate choices. */
    AdminSnapshot.@Nullable OreRuleDraft draft(String familyId) {
        Family family = this.families.get(familyId);
        if (family == null) {
            return null;
        }
        Set<String> selected = selectedCandidates(familyId);
        List<Candidate> candidates = family.candidates().stream()
                .filter(candidate -> selected.contains(candidate.blockId()))
                .toList();
        // A review-only family cannot participate in the safe batch path, but it must still be editable so an
        // administrator can explicitly choose its replacement host. The wizard rejects the blank host on save.
        return toDraft(
                family, candidates.isEmpty() ? List.of(family.candidates().getFirst()) : candidates);
    }

    String searchQuery() {
        return this.searchQuery;
    }

    void setSearchQuery(@Nullable String query) {
        this.searchQuery = query == null ? "" : query;
        this.page = 0;
    }

    boolean showConfigured() {
        return this.showConfigured;
    }

    void toggleShowConfigured() {
        this.showConfigured = !this.showConfigured;
        this.page = 0;
    }

    int page() {
        return this.page;
    }

    void setPage(int requestedPage, int pageCount) {
        this.page = Math.max(0, Math.min(requestedPage, Math.max(1, pageCount) - 1));
    }

    /** Returns the first row of the final discrete local window within one authoritative server page. */
    static int lastLocalWindowOffset(int itemCount, int visibleRows) {
        int boundedItemCount = Math.max(0, itemCount);
        int boundedVisibleRows = Math.max(1, visibleRows);
        return boundedItemCount == 0 ? 0 : ((boundedItemCount - 1) / boundedVisibleRows) * boundedVisibleRows;
    }

    /** Clamps a retained local-window offset after a refreshed server page changes size. */
    static int clampLocalWindowOffset(int requestedOffset, int itemCount, int visibleRows) {
        int boundedVisibleRows = Math.max(1, visibleRows);
        int alignedOffset = Math.max(0, requestedOffset) / boundedVisibleRows * boundedVisibleRows;
        return Math.min(alignedOffset, lastLocalWindowOffset(itemCount, boundedVisibleRows));
    }

    /** Returns whether an arriving server page represents the player's latest filter choices. */
    static boolean responseMatchesFilters(
            @Nullable String desiredQuery,
            boolean desiredShowConfigured,
            @Nullable String responseQuery,
            boolean responseShowConfigured) {
        String desired = desiredQuery == null ? "" : desiredQuery.trim().toLowerCase(Locale.ROOT);
        String response = responseQuery == null ? "" : responseQuery.trim().toLowerCase(Locale.ROOT);
        return desired.equals(response) && desiredShowConfigured == responseShowConfigured;
    }

    /** Clamps a requested server-page index using the same lower/upper bounds as the server projection. */
    static int clampServerPage(int requestedPage, int pageCount) {
        return Math.clamp(requestedPage, 0, Math.max(1, pageCount) - 1);
    }

    /**
     * Classifies whether and how a rejected server result should restart the retained catalog.
     *
     * <p>No-active-scan, expired, and invalid-token outcomes intentionally share the import-session expired message.
     * Registry and base-profile changes also invalidate the retained token. Other failures, including rate limiting,
     * validation, permission, and internal errors, are terminal so a client cannot create an automatic retry loop.
     */
    static CatalogRecovery catalogRecovery(@Nullable String encodedMessage) {
        return AdminLocalizedMessage.decode(encodedMessage)
                .map(AdminLocalizedMessage.Decoded::translationKey)
                .map(key -> switch (key) {
                    case "message.delvefold.import.session.expired" -> CatalogRecovery.RETAIN_SELECTIONS;
                    case "message.delvefold.import.session.registry_changed",
                            "message.delvefold.import.session.base_changed" -> CatalogRecovery.RESET_SELECTIONS;
                    default -> CatalogRecovery.NONE;
                })
                .orElse(CatalogRecovery.NONE);
    }

    /** Recovery behavior for a rejected request made against a retained server catalog token. */
    enum CatalogRecovery {
        NONE,
        RETAIN_SELECTIONS,
        RESET_SELECTIONS;

        /** Returns whether the client should automatically request a fresh server catalog. */
        boolean recoverable() {
            return this != NONE;
        }

        /** Returns whether selections remain safe when the fresh catalog receives a new token. */
        boolean retainSelections() {
            return this == RETAIN_SELECTIONS;
        }
    }

    private Set<String> selectedCandidates(String familyId) {
        return this.selectedCandidateIds.getOrDefault(familyId, new LinkedHashSet<>());
    }

    private static Set<String> defaultCandidates(Family family) {
        String preferredProvider = family.candidates().stream()
                .filter(candidate -> !candidate.reviewRequired())
                .map(Candidate::providerNamespace)
                .distinct()
                .sorted(Comparator.comparing((String provider) -> !provider.equals("minecraft"))
                        .thenComparing(String::compareTo))
                .findFirst()
                .orElse("");
        return family.candidates().stream()
                .filter(candidate ->
                        candidate.providerNamespace().equals(preferredProvider) && !candidate.reviewRequired())
                .map(Candidate::blockId)
                .limit(ProtocolLimits.MAX_VARIANTS)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static boolean matches(Family family, String query) {
        if (family.id().contains(query)
                || family.material().contains(query)
                || family.displayName().toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        return family.candidates().stream()
                .anyMatch(candidate -> candidate.blockId().contains(query)
                        || candidate.providerNamespace().contains(query)
                        || candidate.hostVariant().contains(query));
    }

    private static AdminSnapshot.OreRuleDraft toDraft(Family family, List<Candidate> candidates) {
        Candidate primary = candidates.getFirst();
        List<AdminSnapshot.OreVariantDraft> variants = candidates.stream()
                .map(candidate -> new AdminSnapshot.OreVariantDraft(
                        candidate.blockId(),
                        "",
                        candidate.replaceTag(),
                        Map.of(),
                        AdminSnapshot.OreVariantDraft.MIN_WEIGHT))
                .toList();
        return new AdminSnapshot.OreRuleDraft(
                family.ruleId(),
                true,
                false,
                primary.blockId(),
                variants,
                List.of(TerrainMode.values()),
                List.of("#delvefold:mining_biomes"),
                List.of(),
                List.of(AdminSnapshot.OreBandDraft.defaultBand()));
    }

    /** Immutable presentation model for one logical ore material across provider mods. */
    record Family(
            String id,
            String material,
            String displayName,
            String ruleId,
            boolean configured,
            boolean reviewRequired,
            List<Candidate> candidates) {
        Family {
            id = required(id, "family ID");
            material = required(material, "material");
            displayName = required(displayName, "display name");
            ruleId = required(ruleId, "rule ID");
            candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates")).stream()
                    .sorted(candidateComparator())
                    .toList();
            if (candidates.isEmpty() || candidates.size() > ProtocolLimits.MAX_ORE_LIBRARY_CANDIDATES_PER_FAMILY) {
                throw new IllegalArgumentException(
                        "Ore families need 1-" + ProtocolLimits.MAX_ORE_LIBRARY_CANDIDATES_PER_FAMILY + " candidates");
            }
            if (candidates.stream().map(Candidate::blockId).distinct().count() != candidates.size()) {
                throw new IllegalArgumentException("Ore family candidates must have distinct block IDs");
            }
        }

        List<String> providerNamespaces() {
            return candidates.stream()
                    .map(Candidate::providerNamespace)
                    .distinct()
                    .toList();
        }
    }

    /** Immutable exact block candidate within one provider and host variant. */
    record Candidate(
            String blockId, String providerNamespace, String hostVariant, String replaceTag, boolean reviewRequired) {
        Candidate {
            blockId = required(blockId, "block ID");
            providerNamespace = required(providerNamespace, "provider namespace");
            hostVariant = required(hostVariant, "host variant");
            replaceTag = replaceTag == null ? "" : replaceTag.trim();
            if (!reviewRequired && replaceTag.isBlank()) {
                throw new IllegalArgumentException("replacement tag must not be blank for a safe candidate");
            }
        }
    }

    private static Comparator<Candidate> candidateComparator() {
        return Comparator.comparing(
                        (Candidate candidate) -> !candidate.providerNamespace().equals("minecraft"))
                .thenComparing(Candidate::providerNamespace)
                .thenComparingInt(candidate -> hostOrder(candidate.hostVariant()))
                .thenComparing(Candidate::blockId);
    }

    private static int hostOrder(String hostVariant) {
        return switch (hostVariant) {
            case "stone" -> 0;
            case "deepslate" -> 1;
            case "nether" -> 2;
            case "end" -> 3;
            default -> 4;
        };
    }

    private static String required(@Nullable String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
