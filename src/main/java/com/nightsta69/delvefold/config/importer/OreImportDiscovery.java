package com.nightsta69.delvefold.config.importer;

import com.nightsta69.delvefold.config.importer.OreImportModels.Candidate;
import com.nightsta69.delvefold.config.importer.OreImportModels.DiscoveryOptions;
import com.nightsta69.delvefold.config.importer.OreImportModels.DiscoveryResult;
import com.nightsta69.delvefold.config.importer.OreImportModels.Evidence;
import com.nightsta69.delvefold.config.importer.OreImportModels.Group;
import com.nightsta69.delvefold.config.importer.OreImportModels.HostKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Deterministic, material-first discovery with conservative provider and host-variant inference. */
public final class OreImportDiscovery {
    private static final String FAMILY_NAMESPACE = "delvefold";
    private static final String FAMILY_PATH_PREFIX = "ores/";
    private static final String COMMON_ORES_TAG = "c:ores";
    private static final String CONVENTIONAL_PREFIX = "c:ores/";
    private static final Pattern RESOURCE_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final List<String> REVIEW_HOST_PREFIXES = List.of(
            "nether_",
            "netherrack_",
            "end_",
            "endstone_",
            "end_stone_",
            "blackstone_",
            "basalt_",
            "soul_",
            "sand_",
            "gravel_",
            "tuff_",
            "granite_",
            "diorite_",
            "andesite_");

    private OreImportDiscovery() {}

    /**
     * Discovers conventional and ore-like blocks, then groups probable variants by logical material across providers.
     *
     * <p>Duplicate registry entries are merged, candidates and groups are returned in lexical ID order, and every
     * public discovery bound is applied deterministically. Unsupported IDs or omitted entries set the result's
     * truncation flag. Material-specific common tags take priority over registry names. Name-only families remain
     * available but are marked for review, and ambiguous common-tag assignments never force unrelated materials into
     * one family. Host inference is likewise conservative: ambiguous Nether, End, decorative, or nonstandard variants
     * receive no guessed replacement tag.
     *
     * @param registry deterministic installed-block and tag snapshot
     * @param options discovery options, or {@code null} for modded namespaces only
     * @return immutable, bounded discovery result suitable for server-side preview paging
     */
    public static DiscoveryResult discover(OreImportRegistry registry, @Nullable DiscoveryOptions options) {
        if (registry == null) {
            throw new IllegalArgumentException("Ore import registry is required");
        }
        DiscoveryOptions safeOptions = options == null ? DiscoveryOptions.MODDED_ONLY : options;
        List<OreImportRegistry.BlockEntry> source = registry.blocks() == null ? List.of() : registry.blocks();
        boolean truncated = source.size() > OreImportModels.MAX_SCANNED_BLOCKS;
        int scannedBlocks = Math.min(source.size(), OreImportModels.MAX_SCANNED_BLOCKS);

        // Merge duplicate test/adapter entries before grouping so input iteration never affects output.
        TreeMap<String, TreeSet<String>> tagsByBlock = new TreeMap<>();
        source.stream()
                .filter(java.util.Objects::nonNull)
                .limit(OreImportModels.MAX_SCANNED_BLOCKS)
                .forEach(entry -> tagsByBlock
                        .computeIfAbsent(entry.id(), ignored -> new TreeSet<>())
                        .addAll(entry.tags()));

        TreeMap<String, GroupBuilder> groups = new TreeMap<>();
        for (Map.Entry<String, TreeSet<String>> entry : tagsByBlock.entrySet()) {
            String blockId = entry.getKey();
            if (!supportedResourceId(blockId)) {
                truncated = true;
                continue;
            }
            int separator = blockId.indexOf(':');
            String namespace = blockId.substring(0, separator);
            String path = blockId.substring(separator + 1);
            if (!safeOptions.includeVanilla() && "minecraft".equals(namespace)) {
                continue;
            }

            List<String> conventionalTags = entry.getValue().stream()
                    .filter(OreImportDiscovery::isConventionalOreTag)
                    .filter(OreImportDiscovery::supportedResourceId)
                    .sorted()
                    .toList();
            boolean commonTagged = entry.getValue().contains(COMMON_ORES_TAG);
            boolean oreLikeName = oreLike(path);
            if (conventionalTags.isEmpty() && !commonTagged && !oreLikeName) {
                continue;
            }

            String nameMaterial = materialFromName(path);
            TagResolution tagResolution = resolveConventionalTag(conventionalTags, nameMaterial);
            String material = tagResolution.tag().isEmpty() ? nameMaterial : materialFromTag(tagResolution.tag());
            material = boundedMaterial(material);
            if (material.isEmpty()) {
                truncated = true;
                continue;
            }

            Evidence evidence = !tagResolution.tag().isEmpty()
                    ? Evidence.CONVENTIONAL_TAG
                    : commonTagged ? Evidence.COMMON_ORES_TAG : Evidence.ORE_LIKE_NAME;
            HostKind hostKind =
                    classifyHost(path, material, !tagResolution.tag().isEmpty(), oreLikeName);
            List<String> sourceTags = new ArrayList<>();
            if (commonTagged) {
                sourceTags.add(COMMON_ORES_TAG);
            }
            sourceTags.addAll(conventionalTags);
            if (sourceTags.size() > OreImportModels.MAX_SOURCE_TAGS_PER_CANDIDATE) {
                sourceTags = retainedSourceTags(sourceTags, tagResolution.tag());
                truncated = true;
            }

            Candidate candidate = new Candidate(blockId, hostKind.replaceTag(), hostKind, evidence, sourceTags);
            String groupId = familyId(material);
            String groupMaterial = material;
            boolean identityReviewRequired = tagResolution.ambiguous() || evidence != Evidence.CONVENTIONAL_TAG;
            groups.computeIfAbsent(groupId, ignored -> new GroupBuilder(groupMaterial))
                    .add(candidate, identityReviewRequired);
        }

        List<Group> result = new ArrayList<>();
        for (Map.Entry<String, GroupBuilder> entry : groups.entrySet()) {
            if (result.size() >= OreImportModels.MAX_GROUPS) {
                truncated = true;
                break;
            }
            GroupBuilder builder = entry.getValue();
            List<Candidate> candidates = builder.candidates.values().stream()
                    .limit(OreImportModels.MAX_DISCOVERED_CANDIDATES_PER_GROUP)
                    .toList();
            if (builder.candidates.size() > candidates.size()) {
                truncated = true;
            }
            Evidence evidence = candidates.stream()
                    .map(Candidate::evidence)
                    .max(Comparator.comparingInt(Evidence::strength))
                    .orElse(Evidence.ORE_LIKE_NAME);
            result.add(new Group(
                    entry.getKey(), FAMILY_NAMESPACE, builder.material, evidence, candidates, builder.reviewRequired));
        }
        return new DiscoveryResult(result, truncated, scannedBlocks);
    }

    private static TagResolution resolveConventionalTag(List<String> tags, String nameMaterial) {
        TreeMap<String, String> tagByMaterial = new TreeMap<>();
        for (String tag : tags) {
            String material = materialFromTag(tag);
            if (!material.isEmpty()) {
                tagByMaterial.putIfAbsent(material, tag);
            }
        }
        String exact = tagByMaterial.get(nameMaterial);
        if (exact != null) {
            return new TagResolution(exact, false);
        }
        if (tagByMaterial.size() == 1) {
            return new TagResolution(tagByMaterial.firstEntry().getValue(), false);
        }
        return new TagResolution("", !tagByMaterial.isEmpty());
    }

    private static boolean isConventionalOreTag(String tag) {
        return tag != null && tag.startsWith(CONVENTIONAL_PREFIX) && tag.length() > CONVENTIONAL_PREFIX.length();
    }

    private static List<String> retainedSourceTags(List<String> sourceTags, String identityTag) {
        TreeSet<String> retained = new TreeSet<>(sourceTags);
        while (retained.size() > OreImportModels.MAX_SOURCE_TAGS_PER_CANDIDATE) {
            String removable = retained.last();
            if (removable.equals(identityTag)) {
                removable = retained.lower(removable);
            }
            if (removable == null) {
                break;
            }
            retained.remove(removable);
        }
        return List.copyOf(retained);
    }

    private static boolean oreLike(String path) {
        return path.endsWith("_ore") || path.startsWith("ore_");
    }

    private static String materialFromName(String path) {
        String normalized = path.toLowerCase(Locale.ROOT);
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < normalized.length()) {
            normalized = normalized.substring(slash + 1);
        }
        if (normalized.startsWith("deepslate_")) {
            normalized = normalized.substring("deepslate_".length());
        } else if (normalized.startsWith("stone_")) {
            normalized = normalized.substring("stone_".length());
        }
        if (normalized.endsWith("_ore")) {
            normalized = normalized.substring(0, normalized.length() - "_ore".length());
        } else if (normalized.startsWith("ore_")) {
            normalized = normalized.substring("ore_".length());
        }
        return sanitizeMaterial(normalized);
    }

    private static String materialFromTag(String tag) {
        String path = tag.substring(CONVENTIONAL_PREFIX.length());
        return sanitizeMaterial(path);
    }

    private static String sanitizeMaterial(String value) {
        String normalized = value == null
                ? ""
                : value.toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9_./-]", "_")
                        .replaceAll("_+", "_");
        while (normalized.startsWith("/") || normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/") || normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String boundedMaterial(String material) {
        int available = OreImportModels.MAX_ID_LENGTH - FAMILY_NAMESPACE.length() - 1 - FAMILY_PATH_PREFIX.length();
        if (available <= 0 || material.isEmpty() || material.length() > available) {
            return "";
        }
        String bounded = material;
        while (!bounded.isEmpty() && (bounded.endsWith("/") || bounded.endsWith("."))) {
            bounded = bounded.substring(0, bounded.length() - 1);
        }
        return bounded;
    }

    private static String familyId(String material) {
        return FAMILY_NAMESPACE + ':' + FAMILY_PATH_PREFIX + material;
    }

    private static HostKind classifyHost(String path, String material, boolean conventionNamed, boolean oreLikeName) {
        String simplePath = path.substring(path.lastIndexOf('/') + 1);
        if (simplePath.startsWith("deepslate_")) {
            return HostKind.DEEPSLATE;
        }
        if (simplePath.startsWith("stone_")) {
            return HostKind.STONE;
        }
        if (REVIEW_HOST_PREFIXES.stream().anyMatch(simplePath::startsWith)) {
            return HostKind.REVIEW_REQUIRED;
        }
        if (conventionNamed) {
            String simpleMaterial = material.substring(material.lastIndexOf('/') + 1);
            if (simplePath.equals(simpleMaterial + "_ore") || simplePath.equals("ore_" + simpleMaterial)) {
                return HostKind.STONE;
            }
            return HostKind.REVIEW_REQUIRED;
        }
        return oreLikeName ? HostKind.STONE : HostKind.REVIEW_REQUIRED;
    }

    private static boolean supportedResourceId(String id) {
        return id != null
                && id.length() <= OreImportModels.MAX_ID_LENGTH
                && RESOURCE_ID.matcher(id).matches();
    }

    private record TagResolution(String tag, boolean ambiguous) {}

    private static final class GroupBuilder {
        private final String material;
        private final TreeMap<String, Candidate> candidates = new TreeMap<>();
        private boolean reviewRequired;

        private GroupBuilder(String material) {
            this.material = material;
        }

        private void add(Candidate candidate, boolean identityReviewRequired) {
            candidates.merge(candidate.blockId(), candidate, GroupBuilder::mergeCandidate);
            reviewRequired |= identityReviewRequired;
        }

        private static Candidate mergeCandidate(Candidate left, Candidate right) {
            Evidence evidence = Evidence.strongest(left.evidence(), right.evidence());
            HostKind host = left.hostKind() == right.hostKind() ? left.hostKind() : HostKind.REVIEW_REQUIRED;
            Set<String> mergedTags = new TreeSet<>(left.sourceTags());
            mergedTags.addAll(right.sourceTags());
            List<String> tags = mergedTags.stream()
                    .limit(OreImportModels.MAX_SOURCE_TAGS_PER_CANDIDATE)
                    .toList();
            return new Candidate(left.blockId(), host.replaceTag(), host, evidence, tags);
        }
    }
}
