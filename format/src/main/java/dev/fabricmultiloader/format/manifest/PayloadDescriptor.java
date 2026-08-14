package dev.fabricmultiloader.format.manifest;

import dev.fabricmultiloader.format.version.SemVer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One version-specific implementation inside a universal jar.
 *
 * <p>A payload is a complete, separately built and remapped Fabric mod. Everything here is either a
 * fact about the file that the assembler measured (hash, size, class file version, resource digest)
 * or a constraint the loader will evaluate. Nothing is a hint: the same data drives the disjointness
 * proof at build time, the integrity check at startup, and the diagnostic report when no payload
 * matches.
 */
public final class PayloadDescriptor {

    private final String id;
    private final String modId;
    private final SemVer modVersion;
    private final String displayName;
    private final String file;
    private final String sha256;
    private final long size;
    private final int classfileMajor;
    private final int priority;
    private final String platformFactory;
    private final List<String> packages;
    private final Requirements requires;
    private final List<String> provides;
    private final List<String> breaks;
    private final MappingsInfo mappings;
    private final List<MixinConfigRef> mixins;
    private final List<String> refmaps;
    private final String accessWidener;
    private final List<String> nestedJars;
    private final String resourcesDigest;
    private final List<String> capabilities;

    private PayloadDescriptor(Builder builder) {
        this.id = builder.id;
        this.modId = builder.modId;
        this.modVersion = builder.modVersion;
        this.displayName = builder.displayName;
        this.file = builder.file;
        this.sha256 = builder.sha256;
        this.size = builder.size;
        this.classfileMajor = builder.classfileMajor;
        this.priority = builder.priority;
        this.platformFactory = builder.platformFactory;
        this.packages = immutable(builder.packages);
        this.requires = builder.requires;
        this.provides = immutable(builder.provides);
        this.breaks = immutable(builder.breaks);
        this.mappings = builder.mappings;
        this.mixins = Collections.unmodifiableList(new ArrayList<MixinConfigRef>(builder.mixins));
        this.refmaps = immutable(builder.refmaps);
        this.accessWidener = builder.accessWidener;
        this.nestedJars = immutable(builder.nestedJars);
        this.resourcesDigest = builder.resourcesDigest;
        this.capabilities = immutable(builder.capabilities);
    }

    /** Starts a builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** Short project-local id, e.g. {@code mc1214}. Appears in logs, task names and directories. */
    public String id() {
        return id;
    }

    /** The Fabric mod id of this payload, e.g. {@code examplemod-mc1214}. */
    public String modId() {
        return modId;
    }

    /** The payload's version, typically {@code <containerVersion>+mc<mcVersion>}. */
    public SemVer modVersion() {
        return modVersion;
    }

    /** Human-readable name shown in mod lists. */
    public String displayName() {
        return displayName;
    }

    /** Path of the nested jar inside the container. */
    public String file() {
        return file;
    }

    /** SHA-256 of the nested jar, lower-case hex. */
    public String sha256() {
        return sha256;
    }

    /** Size of the nested jar in bytes. */
    public long size() {
        return size;
    }

    /** Class file major version of every class in this payload — 61, 65, 69, … */
    public int classfileMajor() {
        return classfileMajor;
    }

    /** Build-time precedence for range subtraction. Never evaluated at runtime. */
    public int priority() {
        return priority;
    }

    /** Fully qualified name of the {@code PlatformFactory} implementation. */
    public String platformFactory() {
        return platformFactory;
    }

    /** Package prefixes this payload owns; must not overlap other payloads or common. */
    public List<String> packages() {
        return packages;
    }

    /** What the payload demands of its environment. */
    public Requirements requires() {
        return requires;
    }

    /** Alias ids this payload provides — always includes the container's payload alias. */
    public List<String> provides() {
        return provides;
    }

    /** Mod ids this payload declares incompatible: every other payload of the same container. */
    public List<String> breaks() {
        return breaks;
    }

    /** Which mappings this payload was built against. */
    public MappingsInfo mappings() {
        return mappings;
    }

    /** Mixin configs registered by this payload. */
    public List<MixinConfigRef> mixins() {
        return mixins;
    }

    /** Refmap file names present in this payload. */
    public List<String> refmaps() {
        return refmaps;
    }

    /** Access widener file name, or {@code null}. */
    public String accessWidener() {
        return accessWidener;
    }

    /** Libraries nested inside this payload. */
    public List<String> nestedJars() {
        return nestedJars;
    }

    /** Digest over the payload's assets and data, for cross-payload drift detection. */
    public String resourcesDigest() {
        return resourcesDigest;
    }

    /** Capability ids this payload implements. */
    public List<String> capabilities() {
        return capabilities;
    }

    /** Whether this payload declares the given capability. */
    public boolean hasCapability(String capabilityId) {
        return capabilities.contains(capabilityId);
    }

    /** Whether the {@code platformFactory} sits inside one of this payload's declared packages. */
    public boolean isPlatformFactoryInsidePackages() {
        return Identifiers.isInsideAnyPackage(platformFactory, packages);
    }

    @Override
    public String toString() {
        return id + " (" + modId + " " + modVersion + ", " + requires + ")";
    }

    private static List<String> immutable(List<String> source) {
        return Collections.unmodifiableList(new ArrayList<String>(source));
    }

    /** Mutable builder. */
    public static final class Builder {

        private String id;
        private String modId;
        private SemVer modVersion = SemVer.of(0, 0, 0);
        private String displayName = "";
        private String file;
        private String sha256 = "";
        private long size;
        private int classfileMajor;
        private int priority;
        private String platformFactory;
        private final List<String> packages = new ArrayList<String>();
        private Requirements requires = Requirements.builder().build();
        private final List<String> provides = new ArrayList<String>();
        private final List<String> breaks = new ArrayList<String>();
        private MappingsInfo mappings = new MappingsInfo(MappingsInfo.INTERMEDIARY, "yarn", "");
        private final List<MixinConfigRef> mixins = new ArrayList<MixinConfigRef>();
        private final List<String> refmaps = new ArrayList<String>();
        private String accessWidener;
        private final List<String> nestedJars = new ArrayList<String>();
        private String resourcesDigest = "";
        private final List<String> capabilities = new ArrayList<String>();

        /** Sets the short payload id. */
        public Builder id(String value) {
            this.id = Identifiers.requirePayloadId(value, "payloads[].id");
            return this;
        }

        /** Sets the Fabric mod id. */
        public Builder modId(String value) {
            this.modId = Identifiers.requireModId(value, "payloads[].modId");
            return this;
        }

        /** Sets the payload version. */
        public Builder modVersion(SemVer value) {
            this.modVersion = value;
            return this;
        }

        /** Sets the payload version from a string. */
        public Builder modVersion(String value) {
            return modVersion(SemVer.parse(value));
        }

        /** Sets the display name. */
        public Builder displayName(String value) {
            this.displayName = value == null ? "" : value;
            return this;
        }

        /** Sets the nested jar path. */
        public Builder file(String value) {
            this.file = SafePaths.requireJarPath(value, "payloads[].file");
            return this;
        }

        /** Sets the jar hash and size. */
        public Builder integrity(String sha256Hex, long sizeBytes) {
            this.sha256 = sha256Hex == null ? "" : sha256Hex;
            this.size = sizeBytes;
            return this;
        }

        /** Sets the expected class file major version. */
        public Builder classfileMajor(int value) {
            this.classfileMajor = value;
            return this;
        }

        /** Sets the build-time priority. */
        public Builder priority(int value) {
            this.priority = value;
            return this;
        }

        /** Sets the platform factory class name. */
        public Builder platformFactory(String value) {
            this.platformFactory = Identifiers.requireClassName(value, "payloads[].platformFactory");
            return this;
        }

        /** Adds owned package prefixes. */
        public Builder packages(String... values) {
            for (String value : values) {
                packages.add(Identifiers.requirePackageName(value, "payloads[].packages"));
            }
            return this;
        }

        /** Sets the requirements. */
        public Builder requires(Requirements value) {
            this.requires = value;
            return this;
        }

        /** Adds provided alias ids. */
        public Builder provides(String... values) {
            for (String value : values) {
                provides.add(Identifiers.requireModId(value, "payloads[].provides"));
            }
            return this;
        }

        /** Adds mod ids this payload breaks. */
        public Builder breaks(String... values) {
            for (String value : values) {
                breaks.add(Identifiers.requireModId(value, "payloads[].breaks"));
            }
            return this;
        }

        /** Sets the mappings information. */
        public Builder mappings(MappingsInfo value) {
            this.mappings = value;
            return this;
        }

        /** Adds a mixin config. */
        public Builder mixin(String config, EnvironmentConstraint environment) {
            mixins.add(new MixinConfigRef(config, environment));
            return this;
        }

        /** Adds refmap file names. */
        public Builder refmaps(String... values) {
            for (String value : values) {
                refmaps.add(SafePaths.requireRelativePath(value, "payloads[].refmaps"));
            }
            return this;
        }

        /** Sets the access widener file name, or {@code null} for none. */
        public Builder accessWidener(String value) {
            this.accessWidener = value == null
                    ? null
                    : SafePaths.requireRelativePath(value, "payloads[].accessWidener");
            return this;
        }

        /** Adds nested library jar paths. */
        public Builder nestedJars(String... values) {
            for (String value : values) {
                nestedJars.add(SafePaths.requireJarPath(value, "payloads[].nestedJars"));
            }
            return this;
        }

        /** Sets the resource digest. */
        public Builder resourcesDigest(String value) {
            this.resourcesDigest = value == null ? "" : value;
            return this;
        }

        /** Adds capability ids, ignoring duplicates. */
        public Builder capabilities(String... values) {
            Set<String> unique = new LinkedHashSet<String>(capabilities);
            unique.addAll(Arrays.asList(values));
            capabilities.clear();
            capabilities.addAll(unique);
            return this;
        }

        /**
         * Builds the descriptor.
         *
         * @throws IllegalStateException if a mandatory field was never set — a generator bug,
         *     distinct from the {@code OMNI-3001} a malformed <em>file</em> produces
         */
        public PayloadDescriptor build() {
            requireSet(id, "id");
            requireSet(modId, "modId");
            requireSet(file, "file");
            requireSet(platformFactory, "platformFactory");
            if (packages.isEmpty()) {
                throw new IllegalStateException("payload " + id + " must declare at least one package");
            }
            return new PayloadDescriptor(this);
        }

        private void requireSet(String value, String field) {
            if (value == null) {
                throw new IllegalStateException("payload descriptor is missing '" + field + "'");
            }
        }
    }
}
