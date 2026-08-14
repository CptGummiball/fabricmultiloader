package dev.fabricmultiloader.format.manifest;

import dev.fabricmultiloader.format.version.SemVer;
import dev.fabricmultiloader.format.version.VersionRange;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Identity and policy of the universal jar itself. */
public final class ContainerInfo {

    private final String modId;
    private final SemVer modVersion;
    private final String displayName;
    private final List<String> commonPackages;
    private final CommonPackaging commonPackaging;
    private final int baselineJavaMajor;
    private final RuntimeRef runtime;
    private final SemVer minRuntime;
    private final String payloadAlias;
    private final boolean strict;
    private final boolean verifyIntegrity;

    private ContainerInfo(Builder builder) {
        this.modId = builder.modId;
        this.modVersion = builder.modVersion;
        this.displayName = builder.displayName;
        this.commonPackages =
                Collections.unmodifiableList(new ArrayList<String>(builder.commonPackages));
        this.commonPackaging = builder.commonPackaging;
        this.baselineJavaMajor = builder.baselineJavaMajor;
        this.runtime = builder.runtime;
        this.minRuntime = builder.minRuntime;
        this.payloadAlias = builder.payloadAlias;
        this.strict = builder.strict;
        this.verifyIntegrity = builder.verifyIntegrity;
    }

    /** Starts a builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** The mod id players and other mods see. */
    public String modId() {
        return modId;
    }

    /** The mod version. */
    public SemVer modVersion() {
        return modVersion;
    }

    /** Human-readable name, used in every diagnostic. */
    public String displayName() {
        return displayName;
    }

    /** Package prefixes the container's common code may occupy. */
    public List<String> commonPackages() {
        return commonPackages;
    }

    /** Whether common code lives in the container or is copied into each payload. */
    public CommonPackaging commonPackaging() {
        return commonPackaging;
    }

    /**
     * Class file baseline of the container itself — the minimum across all payloads, because the
     * container is loaded in the oldest supported environment.
     */
    public int baselineJavaMajor() {
        return baselineJavaMajor;
    }

    /** The embedded runtime library. */
    public RuntimeRef runtime() {
        return runtime;
    }

    /**
     * Lowest runtime version able to interpret this manifest.
     *
     * <p>The field that makes the format additively extensible: new optional fields need no schema
     * bump, but a container that genuinely depends on newer semantics raises this and gets a
     * deterministic {@code OMNI-2002} on an older runtime instead of being half-understood.
     */
    public SemVer minRuntime() {
        return minRuntime;
    }

    /**
     * The alias every payload provides.
     *
     * <p>Fabric permits at most one loaded mod per id, aliases included, so this single field buys
     * structural "at most one payload" without any logic of our own.
     */
    public String payloadAlias() {
        return payloadAlias;
    }

    /** Whether a missing payload aborts the launch (default) or merely deactivates the mod. */
    public boolean strict() {
        return strict;
    }

    /** Whether the active payload's SHA-256 is verified at startup. */
    public boolean verifyIntegrity() {
        return verifyIntegrity;
    }

    @Override
    public String toString() {
        return modId + " " + modVersion + " (" + commonPackaging + ", baseline java "
                + baselineJavaMajor + ")";
    }

    /** The runtime library nested into the container. */
    public static final class RuntimeRef {

        private final String modId;
        private final SemVer version;
        private final VersionRange range;
        private final String file;
        private final String sha256;

        /**
         * @param modId always {@code fabricmultiloader} for major 1
         * @param version the embedded version
         * @param range the compatible range the container declares as a dependency
         * @param file path of the nested jar
         * @param sha256 hash of the nested jar
         */
        public RuntimeRef(String modId, SemVer version, VersionRange range, String file, String sha256) {
            this.modId = Identifiers.requireModId(modId, "container.runtime.modId");
            this.version = version;
            this.range = range;
            this.file = SafePaths.requireJarPath(file, "container.runtime.file");
            this.sha256 = sha256 == null ? "" : sha256;
        }

        /** Mod id of the runtime library. */
        public String modId() {
            return modId;
        }

        /** The embedded version. */
        public SemVer version() {
            return version;
        }

        /**
         * The range the container accepts. Loader deduplication picks the highest compatible
         * runtime across every installed universal mod, so this is what stops a newer major from
         * being silently used.
         */
        public VersionRange range() {
            return range;
        }

        /** Path of the nested runtime jar. */
        public String file() {
            return file;
        }

        /** Hash of the nested runtime jar. */
        public String sha256() {
            return sha256;
        }

        @Override
        public String toString() {
            return modId + " " + version + " " + range;
        }
    }

    /** Mutable builder. */
    public static final class Builder {

        private String modId;
        private SemVer modVersion = SemVer.of(0, 0, 0);
        private String displayName = "";
        private final List<String> commonPackages = new ArrayList<String>();
        private CommonPackaging commonPackaging = CommonPackaging.SHARED;
        private int baselineJavaMajor = 8;
        private RuntimeRef runtime;
        private SemVer minRuntime = SemVer.of(1, 0, 0);
        private String payloadAlias;
        private boolean strict = true;
        private boolean verifyIntegrity = true;

        /** Sets the container mod id. */
        public Builder modId(String value) {
            this.modId = Identifiers.requireModId(value, "container.modId");
            return this;
        }

        /** Sets the mod version. */
        public Builder modVersion(SemVer value) {
            this.modVersion = value;
            return this;
        }

        /** Sets the mod version from a string. */
        public Builder modVersion(String value) {
            return modVersion(SemVer.parse(value));
        }

        /** Sets the display name. */
        public Builder displayName(String value) {
            this.displayName = value == null ? "" : value;
            return this;
        }

        /** Adds permitted common package prefixes. */
        public Builder commonPackages(String... values) {
            for (String value : values) {
                commonPackages.add(Identifiers.requirePackageName(value, "container.commonPackages"));
            }
            return this;
        }

        /** Sets the common packaging mode. */
        public Builder commonPackaging(CommonPackaging value) {
            this.commonPackaging = value;
            return this;
        }

        /** Sets the container bytecode baseline. */
        public Builder baselineJavaMajor(int value) {
            this.baselineJavaMajor = value;
            return this;
        }

        /** Sets the embedded runtime reference. */
        public Builder runtime(RuntimeRef value) {
            this.runtime = value;
            return this;
        }

        /** Sets the minimum runtime version able to read this manifest. */
        public Builder minRuntime(SemVer value) {
            this.minRuntime = value;
            return this;
        }

        /** Sets the payload alias id. */
        public Builder payloadAlias(String value) {
            this.payloadAlias = Identifiers.requireModId(value, "container.payloadAlias");
            return this;
        }

        /** Sets strict mode. */
        public Builder strict(boolean value) {
            this.strict = value;
            return this;
        }

        /** Sets integrity verification. */
        public Builder verifyIntegrity(boolean value) {
            this.verifyIntegrity = value;
            return this;
        }

        /** Builds the immutable container info. */
        public ContainerInfo build() {
            if (modId == null) {
                throw new IllegalStateException("container info is missing 'modId'");
            }
            if (payloadAlias == null) {
                throw new IllegalStateException("container info is missing 'payloadAlias'");
            }
            if (commonPackages.isEmpty()) {
                throw new IllegalStateException("container must declare at least one common package");
            }
            if (runtime == null) {
                throw new IllegalStateException("container info is missing 'runtime'");
            }
            return new ContainerInfo(this);
        }
    }
}
