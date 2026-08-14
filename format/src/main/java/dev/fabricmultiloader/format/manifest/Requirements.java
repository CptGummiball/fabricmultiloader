package dev.fabricmultiloader.format.manifest;

import dev.fabricmultiloader.format.version.VersionRange;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Everything a payload demands of its environment.
 *
 * <p>The fields fall into two groups, and the distinction is the reason payload selection is
 * deterministic at all:
 *
 * <ul>
 *   <li><b>Domain constraints</b> — {@link #minecraft()}, {@link #java()} and
 *       {@link #environment()}. These define <em>which</em> payload applies, and the build proves
 *       that no two payloads share a point in their product.</li>
 *   <li><b>Filters</b> — {@link #fabricLoader()}, {@link #mods()} and {@link #optionalMods()}.
 *       These can only ever remove a payload from consideration, never pick between two. Two
 *       payloads differing solely in a filter are a build error ({@code OMNI-1012}), because if
 *       both filters passed, the choice would be undefined.</li>
 * </ul>
 *
 * <p>{@link #optionalMods()} is checked by nothing: it exists so the diagnostic report can tell a
 * user that an optional integration is inactive, which is otherwise indistinguishable from a bug.
 */
public final class Requirements {

    private final VersionRange minecraft;
    private final VersionRange fabricLoader;
    private final VersionRange java;
    private final EnvironmentConstraint environment;
    private final Map<String, VersionRange> mods;
    private final Map<String, VersionRange> optionalMods;

    private Requirements(Builder builder) {
        this.minecraft = builder.minecraft;
        this.fabricLoader = builder.fabricLoader;
        this.java = builder.java;
        this.environment = builder.environment;
        this.mods = Collections.unmodifiableMap(new LinkedHashMap<String, VersionRange>(builder.mods));
        this.optionalMods =
                Collections.unmodifiableMap(new LinkedHashMap<String, VersionRange>(builder.optionalMods));
    }

    /** Starts a builder with {@code *} for every range and {@code *} for the environment. */
    public static Builder builder() {
        return new Builder();
    }

    /** Minecraft versions this payload applies to. A domain constraint. */
    public VersionRange minecraft() {
        return minecraft;
    }

    /** Fabric Loader versions this payload needs. A filter. */
    public VersionRange fabricLoader() {
        return fabricLoader;
    }

    /** Java feature versions this payload needs. A domain constraint. */
    public VersionRange java() {
        return java;
    }

    /** Sides this payload applies to. A domain constraint. */
    public EnvironmentConstraint environment() {
        return environment;
    }

    /** Hard mod dependencies, keyed by mod id. Filters. */
    public Map<String, VersionRange> mods() {
        return mods;
    }

    /** Soft mod dependencies. Never affect selection; reported for diagnosis only. */
    public Map<String, VersionRange> optionalMods() {
        return optionalMods;
    }

    /** The lowest Java feature version this payload accepts, or {@code 0} if unbounded. */
    public int minimumJavaFeatureVersion() {
        if (java.isEmpty() || java.intervals().isEmpty()) {
            return 0;
        }
        return java.intervals().get(0).min() == null ? 0 : java.intervals().get(0).min().major();
    }

    @Override
    public String toString() {
        return "minecraft=" + minecraft + " java=" + java + " env=" + environment
                + (mods.isEmpty() ? "" : " mods=" + mods.keySet());
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Requirements)) {
            return false;
        }
        Requirements that = (Requirements) other;
        return minecraft.equals(that.minecraft)
                && fabricLoader.equals(that.fabricLoader)
                && java.equals(that.java)
                && environment == that.environment
                && mods.equals(that.mods)
                && optionalMods.equals(that.optionalMods);
    }

    @Override
    public int hashCode() {
        int result = minecraft.hashCode();
        result = 31 * result + fabricLoader.hashCode();
        result = 31 * result + java.hashCode();
        result = 31 * result + environment.hashCode();
        result = 31 * result + mods.hashCode();
        return 31 * result + optionalMods.hashCode();
    }

    /** Mutable builder. */
    public static final class Builder {

        private VersionRange minecraft = VersionRange.ALL;
        private VersionRange fabricLoader = VersionRange.ALL;
        private VersionRange java = VersionRange.ALL;
        private EnvironmentConstraint environment = EnvironmentConstraint.BOTH;
        private final Map<String, VersionRange> mods = new LinkedHashMap<String, VersionRange>();
        private final Map<String, VersionRange> optionalMods = new LinkedHashMap<String, VersionRange>();

        /** Sets the Minecraft range. */
        public Builder minecraft(VersionRange range) {
            this.minecraft = range;
            return this;
        }

        /** Sets the Minecraft range from Fabric predicate strings, OR-combined. */
        public Builder minecraft(String... predicates) {
            return minecraft(VersionRange.parse(predicates));
        }

        /** Sets the Fabric Loader range. */
        public Builder fabricLoader(VersionRange range) {
            this.fabricLoader = range;
            return this;
        }

        /** Sets the Fabric Loader range from predicate strings. */
        public Builder fabricLoader(String... predicates) {
            return fabricLoader(VersionRange.parse(predicates));
        }

        /** Sets the Java range. */
        public Builder java(VersionRange range) {
            this.java = range;
            return this;
        }

        /** Sets the Java range from predicate strings. */
        public Builder java(String... predicates) {
            return java(VersionRange.parse(predicates));
        }

        /** Sets the side constraint. */
        public Builder environment(EnvironmentConstraint constraint) {
            this.environment = constraint == null ? EnvironmentConstraint.BOTH : constraint;
            return this;
        }

        /** Adds a hard mod dependency. */
        public Builder mod(String modId, VersionRange range) {
            mods.put(Identifiers.requireModId(modId, "requires.mods"), range);
            return this;
        }

        /** Adds a hard mod dependency from predicate strings. */
        public Builder mod(String modId, String... predicates) {
            return mod(modId, VersionRange.parse(predicates));
        }

        /** Adds a soft mod dependency. */
        public Builder optionalMod(String modId, VersionRange range) {
            optionalMods.put(Identifiers.requireModId(modId, "requires.optionalMods"), range);
            return this;
        }

        /** Adds a soft mod dependency from predicate strings. */
        public Builder optionalMod(String modId, String... predicates) {
            return optionalMod(modId, VersionRange.parse(predicates));
        }

        /** Builds the immutable requirements. */
        public Requirements build() {
            return new Requirements(this);
        }
    }
}
