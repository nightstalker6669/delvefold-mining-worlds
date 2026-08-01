package com.nightsta69.delvefold.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/** Data-selected phase for the shared runtime geology-theme feature. */
public record GeologyThemeConfiguration(Phase phase) implements FeatureConfiguration {
    public static final Codec<GeologyThemeConfiguration> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.xmap(Phase::parse, Phase::serializedName)
                    .fieldOf("phase").forGetter(GeologyThemeConfiguration::phase)
    ).apply(instance, GeologyThemeConfiguration::new));

    public GeologyThemeConfiguration {
        phase = phase == null ? Phase.STRATA : phase;
    }

    public enum Phase {
        STRATA("strata"),
        DECORATIONS("decorations");

        private final String serializedName;

        Phase(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }

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
