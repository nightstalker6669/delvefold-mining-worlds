package com.nightsta69.delvefold.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * Data-selected phase for the shared runtime geology-theme feature.
 *
 * @param phase bounded planning and application phase invoked by this configured feature
 */
public record GeologyThemeConfiguration(Phase phase) implements FeatureConfiguration {
    /** Configured-feature codec consumed by Minecraft's datapack registry loader. */
    public static final Codec<GeologyThemeConfiguration> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(Codec.STRING
                            .xmap(Phase::parse, Phase::serializedName)
                            .fieldOf("phase")
                            .forGetter(GeologyThemeConfiguration::phase))
                    .apply(instance, GeologyThemeConfiguration::new));

    /**
     * Normalizes an absent codec phase to strata placement.
     *
     * @param phase requested generation phase
     */
    public GeologyThemeConfiguration {
        phase = phase == null ? Phase.STRATA : phase;
    }

    /** Independent deterministic generation stream selected by a configured-feature resource. */
    public enum Phase {
        /** Replaces bounded natural stone positions with theme strata. */
        STRATA("strata"),
        /** Places bounded solid decorations and sealed fluids. */
        DECORATIONS("decorations");

        private final String serializedName;

        Phase(String serializedName) {
            this.serializedName = serializedName;
        }

        /**
         * Returns the stable lowercase datapack name.
         *
         * @return serialized phase name
         */
        public String serializedName() {
            return serializedName;
        }

        /**
         * Parses a case-insensitive datapack phase.
         *
         * @param value serialized phase name
         * @return matching phase
         * @throws IllegalArgumentException when the name is unknown
         */
        public static Phase parse(String value) {
            for (Phase phase : values()) {
                if (phase.serializedName.equalsIgnoreCase(value)) {
                    return phase;
                }
            }
            throw new IllegalArgumentException("Unknown geology feature phase: " + value);
        }
    }
}
