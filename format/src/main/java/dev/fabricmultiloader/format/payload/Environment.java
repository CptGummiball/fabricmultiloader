package dev.fabricmultiloader.format.payload;

import dev.fabricmultiloader.format.Side;
import dev.fabricmultiloader.format.version.JavaVersions;
import dev.fabricmultiloader.format.version.SemVer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the runtime detected about the environment it started in.
 *
 * <p>Lives in {@code format} rather than in the runtime because {@link PayloadMatcher} needs it,
 * and the matcher is shared: at startup it selects, and in a diagnostic it explains <em>why</em>
 * each payload was rejected. That second use is the reason the diagnostic can say
 * "fabric-api 0.110.0 is installed, 0.114.0 required" instead of the loader's much less useful
 * "mod not loaded".
 *
 * <p>Immutable; the runtime builds one per launch.
 */
public final class Environment {

    private final SemVer minecraft;
    private final SemVer fabricLoader;
    private final SemVer fabricApi;
    private final int javaMajor;
    private final Side side;
    private final boolean development;
    private final Map<String, SemVer> loadedMods;

    private Environment(Builder builder) {
        this.minecraft = builder.minecraft;
        this.fabricLoader = builder.fabricLoader;
        this.fabricApi = builder.fabricApi;
        this.javaMajor = builder.javaMajor;
        this.side = builder.side;
        this.development = builder.development;
        this.loadedMods =
                Collections.unmodifiableMap(new LinkedHashMap<String, SemVer>(builder.loadedMods));
    }

    /** Starts a builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** The running Minecraft version. */
    public SemVer minecraft() {
        return minecraft;
    }

    /** The running Fabric Loader version. */
    public SemVer fabricLoader() {
        return fabricLoader;
    }

    /** The installed Fabric API version, or {@link SemVer#UNKNOWN} when absent. */
    public SemVer fabricApi() {
        return fabricApi;
    }

    /** The JVM feature version: 8, 17, 21, 25, … */
    public int javaMajor() {
        return javaMajor;
    }

    /** The Java version as a comparable value, for {@code requires.java}. */
    public SemVer javaVersion() {
        return JavaVersions.asVersion(javaMajor);
    }

    /** The physical side. */
    public Side side() {
        return side;
    }

    /** Whether this is a Loom development runtime. */
    public boolean isDevelopment() {
        return development;
    }

    /** Every loaded mod, keyed by mod id. */
    public Map<String, SemVer> loadedMods() {
        return loadedMods;
    }

    /** The version of a loaded mod, or {@code null} if it is not loaded. */
    public SemVer modVersion(String modId) {
        return loadedMods.get(modId);
    }

    /** Whether a mod is loaded. */
    public boolean isModLoaded(String modId) {
        return loadedMods.containsKey(modId);
    }

    @Override
    public String toString() {
        return "mc=" + minecraft + " loader=" + fabricLoader + " fabric-api=" + fabricApi
                + " java=" + javaMajor + " side=" + side + (development ? " (dev)" : "");
    }

    /** Mutable builder. */
    public static final class Builder {

        private SemVer minecraft = SemVer.UNKNOWN;
        private SemVer fabricLoader = SemVer.UNKNOWN;
        private SemVer fabricApi = SemVer.UNKNOWN;
        private int javaMajor = JavaVersions.BASELINE_FEATURE_VERSION;
        private Side side = Side.SERVER;
        private boolean development;
        private final Map<String, SemVer> loadedMods = new LinkedHashMap<String, SemVer>();

        /** Sets the Minecraft version. */
        public Builder minecraft(SemVer value) {
            this.minecraft = value;
            return this;
        }

        /** Sets the Minecraft version from a string, leniently. */
        public Builder minecraft(String value) {
            return minecraft(SemVer.parseLenient(value));
        }

        /** Sets the Fabric Loader version. */
        public Builder fabricLoader(String value) {
            this.fabricLoader = SemVer.parseLenient(value);
            return this;
        }

        /** Sets the Fabric API version. */
        public Builder fabricApi(String value) {
            this.fabricApi = SemVer.parseLenient(value);
            return this;
        }

        /** Sets the JVM feature version. */
        public Builder javaMajor(int value) {
            this.javaMajor = value;
            return this;
        }

        /** Sets the physical side. */
        public Builder side(Side value) {
            this.side = value;
            return this;
        }

        /** Marks this as a development runtime. */
        public Builder development(boolean value) {
            this.development = value;
            return this;
        }

        /**
         * Registers a loaded mod. Also feeds {@code fabric-api} and the loader into the dedicated
         * fields when those ids appear, so a caller enumerating loaded mods gets consistent state
         * without having to special-case them.
         */
        public Builder mod(String modId, String version) {
            SemVer parsed = SemVer.parseLenient(version);
            loadedMods.put(modId, parsed);
            if ("fabric-api".equals(modId) || "fabric".equals(modId)) {
                this.fabricApi = parsed;
            } else if ("fabricloader".equals(modId)) {
                this.fabricLoader = parsed;
            } else if ("minecraft".equals(modId)) {
                this.minecraft = parsed;
            }
            return this;
        }

        /** Builds the immutable environment. */
        public Environment build() {
            return new Environment(this);
        }
    }
}
