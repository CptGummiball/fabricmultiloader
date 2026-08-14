package dev.fabricmultiloader.api.registry;

/**
 * A description of a block.
 *
 * <p>Covers the properties that have been expressible the same way since 1.16 and are set on
 * essentially every custom block. Anything beyond — block entities, custom shapes, redstone
 * behaviour, state properties — is version-specific by nature and belongs in the payload, reachable
 * through a service. Pretending otherwise would produce an abstraction that has to grow with every
 * Minecraft release, which is exactly the maintenance burden this design avoids.
 */
public final class BlockSpec {

    private final float hardness;
    private final float resistance;
    private final boolean requiresTool;
    private final int luminance;
    private final float slipperiness;
    private final SoundGroup soundGroup;

    private BlockSpec(Builder builder) {
        this.hardness = builder.hardness;
        this.resistance = builder.resistance;
        this.requiresTool = builder.requiresTool;
        this.luminance = builder.luminance;
        this.slipperiness = builder.slipperiness;
        this.soundGroup = builder.soundGroup;
    }

    /** The vanilla sound groups that exist in every supported version. */
    public enum SoundGroup {
        /** Stone. */
        STONE,
        /** Wood. */
        WOOD,
        /** Gravel. */
        GRAVEL,
        /** Grass. */
        GRASS,
        /** Metal. */
        METAL,
        /** Glass. */
        GLASS,
        /** Wool. */
        WOOL,
        /** Sand. */
        SAND,
        /** Snow. */
        SNOW
    }

    /** Starts a builder with stone-like defaults. */
    public static Builder builder() {
        return new Builder();
    }

    /** How long the block takes to break. */
    public float hardness() {
        return hardness;
    }

    /** Blast resistance. */
    public float resistance() {
        return resistance;
    }

    /** Whether the correct tool is required for drops. */
    public boolean requiresTool() {
        return requiresTool;
    }

    /** Emitted light level, 0–15. */
    public int luminance() {
        return luminance;
    }

    /** Surface friction; 0.6 is stone, 0.98 is ice. */
    public float slipperiness() {
        return slipperiness;
    }

    /** Which sounds the block makes. */
    public SoundGroup soundGroup() {
        return soundGroup;
    }

    @Override
    public String toString() {
        return "BlockSpec[hardness=" + hardness + ", resistance=" + resistance
                + ", luminance=" + luminance + "]";
    }

    /** Mutable builder. */
    public static final class Builder {

        private float hardness = 1.5f;
        private float resistance = 6.0f;
        private boolean requiresTool;
        private int luminance;
        private float slipperiness = 0.6f;
        private SoundGroup soundGroup = SoundGroup.STONE;

        /** Sets hardness and resistance to the same value, as most blocks do. */
        public Builder strength(float value) {
            this.hardness = value;
            this.resistance = value;
            return this;
        }

        /** Sets how long the block takes to break. */
        public Builder hardness(float value) {
            this.hardness = value;
            return this;
        }

        /** Sets blast resistance. */
        public Builder resistance(float value) {
            this.resistance = value;
            return this;
        }

        /** Requires the correct tool for the block to drop anything. */
        public Builder requiresTool() {
            this.requiresTool = true;
            return this;
        }

        /**
         * Sets the emitted light level.
         *
         * @param level 0–15
         */
        public Builder luminance(int level) {
            if (level < 0 || level > 15) {
                throw new IllegalArgumentException("luminance must be between 0 and 15, got " + level);
            }
            this.luminance = level;
            return this;
        }

        /** Sets surface friction. */
        public Builder slipperiness(float value) {
            this.slipperiness = value;
            return this;
        }

        /** Sets the sound group. */
        public Builder sounds(SoundGroup value) {
            this.soundGroup = value == null ? SoundGroup.STONE : value;
            return this;
        }

        /** Builds the immutable specification. */
        public BlockSpec build() {
            return new BlockSpec(this);
        }
    }
}
