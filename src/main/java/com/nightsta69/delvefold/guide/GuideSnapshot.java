package com.nightsta69.delvefold.guide;

import java.util.List;
import java.util.Objects;

/**
 * Bounded public information suitable for recipe viewers, API consumers, and client payloads.
 *
 * <p>This contract intentionally has no seed, horizontal position, filesystem,
 * confirmation, permission, validation, or administration fields.</p>
 */
public record GuideSnapshot(
        int formatVersion,
        String worldName,
        String terrain,
        String terrainVariant,
        String geologyTheme,
        String activeProfile,
        PortalStatus portalStatus,
        Renewal renewal,
        List<OreEntry> ores,
        boolean truncated) {
    public static final int LEGACY_FORMAT_VERSION = 1;
    public static final int CURRENT_FORMAT_VERSION = 2;

    public GuideSnapshot {
        if (formatVersion < LEGACY_FORMAT_VERSION || formatVersion > CURRENT_FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported guide format version: " + formatVersion);
        }
        worldName = GuideLimits.boundedText(worldName, GuideLimits.MAX_WORLD_NAME_CHARACTERS);
        terrain = GuideLimits.boundedText(terrain, GuideLimits.MAX_IDENTIFIER_CHARACTERS);
        terrainVariant = GuideLimits.boundedText(terrainVariant, GuideLimits.MAX_IDENTIFIER_CHARACTERS);
        geologyTheme = GuideLimits.boundedText(
                geologyTheme == null || geologyTheme.isBlank() ? "classic" : geologyTheme,
                GuideLimits.MAX_IDENTIFIER_CHARACTERS);
        activeProfile = GuideLimits.boundedText(activeProfile, GuideLimits.MAX_IDENTIFIER_CHARACTERS);
        portalStatus = Objects.requireNonNull(portalStatus, "portalStatus");
        renewal = Objects.requireNonNull(renewal, "renewal");
        ores = ores == null ? List.of() : List.copyOf(ores);
        if (ores.size() > GuideLimits.MAX_ORE_ENTRIES) {
            throw new IllegalArgumentException("Too many guide ore entries");
        }
        if (estimatedNetworkBytes(formatVersion, worldName, terrain, terrainVariant, geologyTheme, activeProfile,
                renewal, ores) > GuideLimits.MAX_ESTIMATED_NETWORK_BYTES) {
            throw new IllegalArgumentException("Guide snapshot exceeds its network-size budget");
        }
    }

    /** Source- and binary-compatible constructor for 1.1 guide consumers. */
    public GuideSnapshot(
            int formatVersion,
            String worldName,
            String terrain,
            String terrainVariant,
            String activeProfile,
            PortalStatus portalStatus,
            Renewal renewal,
            List<OreEntry> ores,
            boolean truncated) {
        this(formatVersion, worldName, terrain, terrainVariant, "classic", activeProfile,
                portalStatus, renewal, ores, truncated);
    }

    public int estimatedNetworkBytes() {
        return estimatedNetworkBytes(formatVersion, worldName, terrain, terrainVariant, geologyTheme, activeProfile,
                renewal, ores);
    }

    static int estimatedBaseNetworkBytes(
            String worldName, String terrain, String terrainVariant, String geologyTheme,
            String activeProfile, Renewal renewal) {
        return estimatedNetworkBytes(CURRENT_FORMAT_VERSION, worldName, terrain, terrainVariant, geologyTheme,
                activeProfile,
                renewal, List.of());
    }

    private static int estimatedNetworkBytes(
            int formatVersion,
            String worldName,
            String terrain,
            String terrainVariant,
            String geologyTheme,
            String activeProfile,
            Renewal renewal,
            List<OreEntry> ores) {
        int bytes = 32;
        bytes += GuideLimits.networkStringBytes(worldName);
        bytes += GuideLimits.networkStringBytes(terrain);
        bytes += GuideLimits.networkStringBytes(terrainVariant);
        bytes += GuideLimits.networkStringBytes(geologyTheme);
        bytes += GuideLimits.networkStringBytes(activeProfile);
        bytes += renewal.estimatedNetworkBytes();
        for (OreEntry ore : ores) {
            bytes += ore.estimatedNetworkBytes();
        }
        return bytes;
    }

    public enum PortalStatus {
        UNINITIALIZED,
        DISABLED,
        ENTRY_BLOCKED,
        AVAILABLE
    }

    public record Renewal(boolean enabled, boolean scheduled, boolean due, long remainingSeconds) {
        public Renewal {
            remainingSeconds = Math.clamp(remainingSeconds, 0L, GuideLimits.MAX_RENEWAL_COUNTDOWN_SECONDS);
            if (!enabled || !scheduled) {
                due = false;
                remainingSeconds = 0L;
            }
        }

        int estimatedNetworkBytes() {
            return 16;
        }
    }

    public record OreEntry(
            String ruleId,
            List<Output> outputs,
            Applicability applicability,
            List<HeightBand> heightBands,
            RelativeFrequency relativeFrequency,
            boolean truncated) {
        public OreEntry {
            ruleId = GuideLimits.boundedText(ruleId, GuideLimits.MAX_IDENTIFIER_CHARACTERS);
            outputs = outputs == null ? List.of() : List.copyOf(outputs);
            applicability = Objects.requireNonNull(applicability, "applicability");
            heightBands = heightBands == null ? List.of() : List.copyOf(heightBands);
            relativeFrequency = Objects.requireNonNull(relativeFrequency, "relativeFrequency");
            if (outputs.size() > GuideLimits.MAX_OUTPUTS_PER_ENTRY) {
                throw new IllegalArgumentException("Too many guide outputs");
            }
            if (heightBands.size() > GuideLimits.MAX_HEIGHT_BANDS_PER_ENTRY) {
                throw new IllegalArgumentException("Too many guide height bands");
            }
        }

        int estimatedNetworkBytes() {
            int bytes = 24 + GuideLimits.networkStringBytes(ruleId) + applicability.estimatedNetworkBytes();
            for (Output output : outputs) {
                bytes += output.estimatedNetworkBytes();
            }
            for (HeightBand band : heightBands) {
                bytes += band.estimatedNetworkBytes();
            }
            return bytes;
        }
    }

    public record Output(OutputKind kind, String sourceId, String iconBlockId) {
        public Output {
            kind = Objects.requireNonNull(kind, "kind");
            sourceId = GuideLimits.boundedText(sourceId, GuideLimits.MAX_IDENTIFIER_CHARACTERS);
            iconBlockId = GuideLimits.boundedText(iconBlockId, GuideLimits.MAX_IDENTIFIER_CHARACTERS);
        }

        int estimatedNetworkBytes() {
            return 8 + GuideLimits.networkStringBytes(sourceId) + GuideLimits.networkStringBytes(iconBlockId);
        }
    }

    public enum OutputKind {
        BLOCK,
        BLOCK_TAG
    }

    public record Applicability(
            List<String> terrains,
            boolean appliesToActiveTerrain,
            boolean biomeFiltered,
            List<String> biomeIncludes,
            List<String> biomeExcludes) {
        public Applicability {
            terrains = terrains == null ? List.of() : terrains.stream()
                    .map(value -> GuideLimits.boundedText(value, GuideLimits.MAX_IDENTIFIER_CHARACTERS))
                    .toList();
            if (terrains.size() > GuideLimits.MAX_APPLICABLE_TERRAINS) {
                throw new IllegalArgumentException("Too many applicable terrains");
            }
            biomeIncludes = boundedSelectors(biomeIncludes);
            biomeExcludes = boundedSelectors(biomeExcludes);
        }

        int estimatedNetworkBytes() {
            int bytes = 8;
            for (String terrain : terrains) {
                bytes += GuideLimits.networkStringBytes(terrain);
            }
            for (String selector : biomeIncludes) {
                bytes += GuideLimits.networkStringBytes(selector);
            }
            for (String selector : biomeExcludes) {
                bytes += GuideLimits.networkStringBytes(selector);
            }
            return bytes;
        }

        private static List<String> boundedSelectors(List<String> values) {
            List<String> safe = values == null ? List.of() : values.stream()
                    .map(value -> GuideLimits.boundedText(value, GuideLimits.MAX_IDENTIFIER_CHARACTERS))
                    .toList();
            if (safe.size() > GuideLimits.MAX_BIOME_SELECTORS_PER_LIST) {
                throw new IllegalArgumentException("Too many guide biome selectors");
            }
            return safe;
        }
    }

    public record HeightBand(
            String bandId,
            String distribution,
            int minY,
            int maxY,
            int bestMinY,
            int bestMaxY,
            int veinSize) {
        public HeightBand {
            bandId = GuideLimits.boundedText(bandId, GuideLimits.MAX_IDENTIFIER_CHARACTERS);
            distribution = GuideLimits.boundedText(distribution, GuideLimits.MAX_IDENTIFIER_CHARACTERS);
            if (minY > maxY || bestMinY < minY || bestMaxY > maxY || bestMinY > bestMaxY) {
                throw new IllegalArgumentException("Invalid guide height range");
            }
            if (veinSize < 1 || veinSize > 64) {
                throw new IllegalArgumentException("Invalid guide vein size");
            }
        }

        int estimatedNetworkBytes() {
            return 32 + GuideLimits.networkStringBytes(bandId) + GuideLimits.networkStringBytes(distribution);
        }
    }

    public enum RelativeFrequency {
        NOT_APPLICABLE,
        NONE,
        VERY_RARE,
        RARE,
        UNCOMMON,
        COMMON,
        ABUNDANT
    }
}
