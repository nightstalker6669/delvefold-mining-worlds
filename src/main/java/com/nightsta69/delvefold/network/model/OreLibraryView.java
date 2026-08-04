package com.nightsta69.delvefold.network.model;

import com.nightsta69.delvefold.config.importer.OreImportModels.Evidence;
import com.nightsta69.delvefold.config.importer.OreImportModels.HostKind;
import com.nightsta69.delvefold.network.ProtocolLimits;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Bounded, read-only page of server-discovered logical ore families for the Unified Ores picker.
 *
 * @param catalogToken opaque player-bound token for the exact retained discovery catalog
 * @param expectedOreRevision non-negative active ore revision used by a later atomic add
 * @param page zero-based page index
 * @param pageCount positive page count derived from the filtered total
 * @param totalFamilies number of families retained after server-side filtering
 * @param query normalized server-side search query
 * @param showConfigured whether already-configured families are retained
 * @param truncated whether discovery omitted source detail because of a safety bound
 * @param families bounded immutable page contents
 */
public record OreLibraryView(
        String catalogToken,
        long expectedOreRevision,
        int page,
        int pageCount,
        int totalFamilies,
        String query,
        boolean showConfigured,
        boolean truncated,
        List<Family> families) {

    /**
     * Validates paging metadata and defensively owns the bounded family page.
     *
     * @param catalogToken opaque player-bound token for the exact registry catalog represented by this page
     * @param expectedOreRevision non-negative active ore revision used by a later atomic add
     * @param page zero-based page index
     * @param pageCount positive page count derived from the filtered family total
     * @param totalFamilies filtered family total
     * @param query normalized server-side search query
     * @param showConfigured whether already-configured families are retained
     * @param truncated whether discovery omitted candidates or families because of a safety bound
     * @param families immutable-by-convention page contents
     */
    public OreLibraryView(
            @Nullable String catalogToken,
            long expectedOreRevision,
            int page,
            int pageCount,
            int totalFamilies,
            @Nullable String query,
            boolean showConfigured,
            boolean truncated,
            @Nullable List<Family> families) {
        String normalizedToken = catalogToken == null ? "" : catalogToken.trim();
        if (normalizedToken.isEmpty() || normalizedToken.length() > ProtocolLimits.MAX_IMPORT_TOKEN_LENGTH) {
            throw new IllegalArgumentException("Invalid ore library catalog token");
        }
        if (expectedOreRevision < 0L) {
            throw new IllegalArgumentException("Ore library revision cannot be negative");
        }
        if (totalFamilies < 0 || totalFamilies > ProtocolLimits.MAX_IMPORT_GROUPS) {
            throw new IllegalArgumentException("Invalid ore library family total: " + totalFamilies);
        }
        int expectedPages = Math.max(
                1,
                (totalFamilies + ProtocolLimits.MAX_ORE_LIBRARY_FAMILIES_PER_PAGE - 1)
                        / ProtocolLimits.MAX_ORE_LIBRARY_FAMILIES_PER_PAGE);
        if (pageCount != expectedPages || page < 0 || page >= pageCount) {
            throw new IllegalArgumentException("Invalid ore library paging metadata");
        }
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.length() > ProtocolLimits.MAX_ORE_LIBRARY_QUERY_LENGTH
                || normalizedQuery.codePointCount(0, normalizedQuery.length())
                        > ProtocolLimits.MAX_ORE_LIBRARY_QUERY_LENGTH) {
            throw new IllegalArgumentException("Ore library query is too long");
        }
        List<Family> copy = families == null ? List.of() : List.copyOf(families);
        int expectedPageSize = Math.min(
                ProtocolLimits.MAX_ORE_LIBRARY_FAMILIES_PER_PAGE,
                Math.max(0, totalFamilies - page * ProtocolLimits.MAX_ORE_LIBRARY_FAMILIES_PER_PAGE));
        if (copy.size() != expectedPageSize
                || copy.stream().map(Family::id).distinct().count() != copy.size()) {
            throw new IllegalArgumentException("Ore library page contents do not match its bounded total");
        }
        this.catalogToken = normalizedToken;
        this.expectedOreRevision = expectedOreRevision;
        this.page = page;
        this.pageCount = pageCount;
        this.totalFamilies = totalFamilies;
        this.query = normalizedQuery;
        this.showConfigured = showConfigured;
        this.truncated = truncated;
        this.families = copy;
    }

    /**
     * Summary of one logical material family, such as copper, across installed providers and host variants.
     *
     * @param id stable server-issued family identifier
     * @param material normalized logical material name
     * @param suggestedRuleId collision-free rule identifier reserved against the complete active profile and catalog
     * @param preferredBlockId exact block used as the family icon and default provider representative
     * @param preferredHostKind authoritative host classification for the preferred representative
     * @param providerCount number of distinct installed mod namespaces represented
     * @param candidateCount number of retained exact registered blocks
     * @param importableCandidateCount number of candidates with a safe inferred replacement host
     * @param evidence strongest evidence supporting the material identity
     * @param configured whether the active profile already targets any member of this family
     * @param reviewRequired whether material identity is ambiguous or no candidate has a safe inferred host
     * @param overflow whether installed candidates exceed one saved rule's 16-target limit
     */
    public record Family(
            String id,
            String material,
            String suggestedRuleId,
            String preferredBlockId,
            HostKind preferredHostKind,
            int providerCount,
            int candidateCount,
            int importableCandidateCount,
            Evidence evidence,
            boolean configured,
            boolean reviewRequired,
            boolean overflow) {
        private static final Pattern RULE_ID = Pattern.compile("[a-z0-9_.-]+");

        /** Validates identifiers, counts, and discovery evidence. */
        public Family {
            id = boundedId(id, "family ID");
            material = boundedId(material, "material");
            suggestedRuleId = boundedId(suggestedRuleId, "suggested rule ID");
            if (!RULE_ID.matcher(suggestedRuleId).matches()) {
                throw new IllegalArgumentException("Invalid ore library suggested rule ID");
            }
            preferredBlockId = boundedId(preferredBlockId, "preferred block ID");
            Objects.requireNonNull(preferredHostKind, "preferredHostKind");
            if (providerCount < 1
                    || providerCount > ProtocolLimits.MAX_ORE_LIBRARY_CANDIDATES_PER_FAMILY
                    || candidateCount < 1
                    || candidateCount > ProtocolLimits.MAX_ORE_LIBRARY_CANDIDATES_PER_FAMILY
                    || providerCount > candidateCount
                    || importableCandidateCount < 0
                    || importableCandidateCount > candidateCount) {
                throw new IllegalArgumentException("Invalid ore library family counts");
            }
            Objects.requireNonNull(evidence, "evidence");
            if ((importableCandidateCount == 0) != reviewRequired
                    || (importableCandidateCount == 0) != (preferredHostKind == HostKind.REVIEW_REQUIRED)) {
                throw new IllegalArgumentException("Ore library family review state is inconsistent");
            }
        }

        private static String boundedId(@Nullable String value, String label) {
            String normalized = value == null ? "" : value.trim();
            if (normalized.isEmpty() || normalized.length() > ProtocolLimits.ID_LENGTH) {
                throw new IllegalArgumentException("Invalid ore library " + label);
            }
            return normalized;
        }
    }
}
