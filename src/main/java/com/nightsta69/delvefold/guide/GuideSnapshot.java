package com.nightsta69.delvefold.guide;

import java.util.List;
import java.util.Objects;

/**
 * Bounded public information suitable for recipe viewers, API consumers, and client payloads.
 *
 * <p>This contract intentionally has no seed, horizontal position, filesystem, confirmation, permission, validation, or
 * administration fields.
 *
 * @param formatVersion guide format version in the inclusive supported range
 * @param worldName bounded player-facing mining-world name
 * @param terrain serialized active terrain, or {@code uninitialized}
 * @param terrainVariant serialized active terrain variant
 * @param geologyTheme serialized geology theme
 * @param activeProfile bounded active ore-profile identifier
 * @param portalStatus public portal availability without administrative diagnostics
 * @param renewal bounded public renewal status and countdown
 * @param ores immutable bounded ore-entry list
 * @param truncated whether server-side safety limits omitted otherwise eligible guide content
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
    /** Oldest guide format accepted for source, binary, and wire compatibility. */
    public static final int LEGACY_FORMAT_VERSION = 1;

    /** Guide format emitted by the current server and codec. */
    public static final int CURRENT_FORMAT_VERSION = 2;

    /**
     * Validates bounds and defensively copies every collection component.
     *
     * @throws IllegalArgumentException if the format, entry count, or estimated payload size exceeds public limits
     * @throws NullPointerException if required status components are absent
     */
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
        Objects.requireNonNull(portalStatus, "portalStatus");
        Objects.requireNonNull(renewal, "renewal");
        ores = ores == null ? List.of() : List.copyOf(ores);
        if (ores.size() > GuideLimits.MAX_ORE_ENTRIES) {
            throw new IllegalArgumentException("Too many guide ore entries");
        }
        if (estimatedNetworkBytes(
                        formatVersion, worldName, terrain, terrainVariant, geologyTheme, activeProfile, renewal, ores)
                > GuideLimits.MAX_ESTIMATED_NETWORK_BYTES) {
            throw new IllegalArgumentException("Guide snapshot exceeds its network-size budget");
        }
    }

    /**
     * Creates a classic-theme snapshot through the source- and binary-compatible 1.1 constructor.
     *
     * @param formatVersion guide format version
     * @param worldName bounded player-facing world name
     * @param terrain serialized active terrain
     * @param terrainVariant serialized terrain variant
     * @param activeProfile active ore-profile identifier
     * @param portalStatus public portal status
     * @param renewal public renewal status
     * @param ores bounded ore entries copied by the canonical constructor
     * @param truncated whether content was omitted due to public limits
     */
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
        this(
                formatVersion,
                worldName,
                terrain,
                terrainVariant,
                "classic",
                activeProfile,
                portalStatus,
                renewal,
                ores,
                truncated);
    }

    /**
     * Estimates the encoded size using the conservative public guide budget model.
     *
     * @return estimated payload bytes, including length-prefix headroom
     */
    public int estimatedNetworkBytes() {
        return estimatedNetworkBytes(
                formatVersion, worldName, terrain, terrainVariant, geologyTheme, activeProfile, renewal, ores);
    }

    static int estimatedBaseNetworkBytes(
            String worldName,
            String terrain,
            String terrainVariant,
            String geologyTheme,
            String activeProfile,
            Renewal renewal) {
        return estimatedNetworkBytes(
                CURRENT_FORMAT_VERSION,
                worldName,
                terrain,
                terrainVariant,
                geologyTheme,
                activeProfile,
                renewal,
                List.of());
    }

    private static int estimatedNetworkBytes(
            // Retained for the legacy method descriptor and versioned wire-size contract.
            @SuppressWarnings("UnusedVariable") int formatVersion,
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

    /** Public, redacted portal availability states shown to ordinary guide readers. */
    public enum PortalStatus {
        /** The mining world has not been initialized. */
        UNINITIALIZED,
        /** Portal travel is disabled by public settings. */
        DISABLED,
        /** New entries are temporarily blocked by a lifecycle operation. */
        ENTRY_BLOCKED,
        /** Portal travel is configured and currently available. */
        AVAILABLE
    }

    /**
     * Immutable public renewal status with a bounded countdown.
     *
     * @param enabled whether renewal is enabled
     * @param scheduled whether a future renewal time is available
     * @param due whether renewal is currently due
     * @param remainingSeconds seconds until renewal, clamped to the public maximum
     */
    public record Renewal(boolean enabled, boolean scheduled, boolean due, long remainingSeconds) {
        /** Normalizes disabled, unscheduled, due, and countdown state into a consistent public representation. */
        public Renewal {
            remainingSeconds = Math.clamp(remainingSeconds, 0L, GuideLimits.MAX_RENEWAL_COUNTDOWN_SECONDS);
            if (!enabled || !scheduled) {
                due = false;
                remainingSeconds = 0L;
            }
        }

        /**
         * Estimates this fixed-width wire fragment.
         *
         * @return conservative encoded size in bytes
         */
        int estimatedNetworkBytes() {
            return 16;
        }
    }

    /**
     * Immutable guide description of one enabled ore rule.
     *
     * @param ruleId bounded rule identifier
     * @param outputs immutable bounded exact-block or block-tag outputs
     * @param applicability public terrain and biome applicability
     * @param heightBands immutable bounded distribution summaries
     * @param relativeFrequency workload-relative frequency category
     * @param truncated whether this entry omitted detail due to a nested bound
     */
    public record OreEntry(
            String ruleId,
            List<Output> outputs,
            Applicability applicability,
            List<HeightBand> heightBands,
            RelativeFrequency relativeFrequency,
            boolean truncated) {
        /**
         * Bounds identifiers, defensively copies lists, and validates nested entry counts.
         *
         * @throws IllegalArgumentException if output or height-band counts exceed guide limits
         * @throws NullPointerException if applicability or relative frequency is absent
         */
        public OreEntry {
            ruleId = GuideLimits.boundedText(ruleId, GuideLimits.MAX_IDENTIFIER_CHARACTERS);
            outputs = outputs == null ? List.of() : List.copyOf(outputs);
            Objects.requireNonNull(applicability, "applicability");
            heightBands = heightBands == null ? List.of() : List.copyOf(heightBands);
            Objects.requireNonNull(relativeFrequency, "relativeFrequency");
            if (outputs.size() > GuideLimits.MAX_OUTPUTS_PER_ENTRY) {
                throw new IllegalArgumentException("Too many guide outputs");
            }
            if (heightBands.size() > GuideLimits.MAX_HEIGHT_BANDS_PER_ENTRY) {
                throw new IllegalArgumentException("Too many guide height bands");
            }
        }

        /**
         * Estimates this entry's encoded size including all nested values.
         *
         * @return conservative encoded size in bytes
         */
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

    /**
     * Immutable output reference suitable for display without registry ownership transfer.
     *
     * @param kind whether {@code sourceId} names an exact block or a block tag
     * @param sourceId bounded registry or tag identifier without a leading hash marker
     * @param iconBlockId optional bounded representative exact block identifier; empty when unavailable
     */
    public record Output(OutputKind kind, String sourceId, String iconBlockId) {
        /** Validates the output kind and bounds both identifiers. */
        public Output {
            Objects.requireNonNull(kind, "kind");
            sourceId = GuideLimits.boundedText(sourceId, GuideLimits.MAX_IDENTIFIER_CHARACTERS);
            iconBlockId = GuideLimits.boundedText(iconBlockId, GuideLimits.MAX_IDENTIFIER_CHARACTERS);
        }

        /**
         * Estimates this output's encoded size.
         *
         * @return conservative encoded size in bytes
         */
        int estimatedNetworkBytes() {
            return 8 + GuideLimits.networkStringBytes(sourceId) + GuideLimits.networkStringBytes(iconBlockId);
        }
    }

    /** Identifies how a guide output source should be interpreted. */
    public enum OutputKind {
        /** Source identifier names one exact registered block. */
        BLOCK,
        /** Source identifier names a block tag whose members are eligible outputs. */
        BLOCK_TAG
    }

    /**
     * Immutable public applicability summary for one ore rule.
     *
     * @param terrains immutable serialized terrain modes configured on the rule
     * @param appliesToActiveTerrain whether the rule applies to the server's current terrain
     * @param biomeFiltered whether the rule narrows the default mining-biome set
     * @param biomeIncludes immutable bounded include selectors
     * @param biomeExcludes immutable bounded exclude selectors
     */
    public record Applicability(
            List<String> terrains,
            boolean appliesToActiveTerrain,
            boolean biomeFiltered,
            List<String> biomeIncludes,
            List<String> biomeExcludes) {
        /**
         * Bounds and defensively copies terrain and biome selector lists.
         *
         * @throws IllegalArgumentException if a list exceeds its public guide limit
         */
        public Applicability {
            terrains = terrains == null
                    ? List.of()
                    : terrains.stream()
                            .map(value -> GuideLimits.boundedText(value, GuideLimits.MAX_IDENTIFIER_CHARACTERS))
                            .toList();
            if (terrains.size() > GuideLimits.MAX_APPLICABLE_TERRAINS) {
                throw new IllegalArgumentException("Too many applicable terrains");
            }
            biomeIncludes = boundedSelectors(biomeIncludes);
            biomeExcludes = boundedSelectors(biomeExcludes);
        }

        /**
         * Estimates this applicability fragment's encoded size.
         *
         * @return conservative encoded size in bytes
         */
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
            List<String> safe = values == null
                    ? List.of()
                    : values.stream()
                            .map(value -> GuideLimits.boundedText(value, GuideLimits.MAX_IDENTIFIER_CHARACTERS))
                            .toList();
            if (safe.size() > GuideLimits.MAX_BIOME_SELECTORS_PER_LIST) {
                throw new IllegalArgumentException("Too many guide biome selectors");
            }
            return safe;
        }
    }

    /**
     * Immutable vertical distribution summary for one configured spawn band.
     *
     * @param bandId bounded band identifier
     * @param distribution serialized height-distribution name
     * @param minY inclusive configured minimum block Y
     * @param maxY inclusive configured maximum block Y
     * @param bestMinY inclusive lower edge of the advertised best-height interval
     * @param bestMaxY inclusive upper edge of the advertised best-height interval
     * @param veinSize effective vein size in the inclusive range 1-64
     */
    public record HeightBand(
            String bandId, String distribution, int minY, int maxY, int bestMinY, int bestMaxY, int veinSize) {
        /**
         * Bounds identifiers and validates inclusive vertical intervals and vein size.
         *
         * @throws IllegalArgumentException if height ordering or vein size is invalid
         */
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

        /**
         * Estimates this height band's encoded size.
         *
         * @return conservative encoded size in bytes
         */
        int estimatedNetworkBytes() {
            return 32 + GuideLimits.networkStringBytes(bandId) + GuideLimits.networkStringBytes(distribution);
        }
    }

    /** Coarse, non-coordinate frequency derived relative to the most expensive active rule. */
    public enum RelativeFrequency {
        /** Rule does not apply to the active terrain. */
        NOT_APPLICABLE,
        /** Rule has no positive active placement work. */
        NONE,
        /** Positive work is at most ten percent of the active maximum. */
        VERY_RARE,
        /** Positive work is at most twenty-five percent of the active maximum. */
        RARE,
        /** Positive work is at most fifty percent of the active maximum. */
        UNCOMMON,
        /** Positive work is at most seventy-five percent of the active maximum. */
        COMMON,
        /** Positive work exceeds seventy-five percent of the active maximum. */
        ABUNDANT
    }
}
