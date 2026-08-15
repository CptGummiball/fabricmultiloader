package dev.fabricmultiloader.testing;

import dev.fabricmultiloader.format.OmniFormat;
import dev.fabricmultiloader.format.manifest.CommonPackaging;
import dev.fabricmultiloader.format.manifest.ContainerInfo;
import dev.fabricmultiloader.format.manifest.ContainerManifest;
import dev.fabricmultiloader.format.manifest.EntrypointSet;
import dev.fabricmultiloader.format.manifest.EnvironmentConstraint;
import dev.fabricmultiloader.format.manifest.MappingsInfo;
import dev.fabricmultiloader.format.manifest.PayloadDescriptor;
import dev.fabricmultiloader.format.manifest.Requirements;
import dev.fabricmultiloader.format.version.JavaVersions;
import dev.fabricmultiloader.format.version.SemVer;
import dev.fabricmultiloader.format.version.VersionRange;
import java.util.ArrayList;
import java.util.List;

/**
 * A container manifest in a handful of lines, for tests that care about one property of it.
 *
 * <p>A realistic manifest has around forty fields, of which any given test cares about two.
 * Everything else here has a default that is coherent with the rest, so a test reads as its own
 * subject: {@code manifest().payload("mc1201", "1.20.1", 17).payload("mc1214", "1.21.4", 21)} says
 * "two payloads on different Minecraft and Java versions" and nothing else.
 *
 * <p>The defaults are not arbitrary — {@link #baselineJavaMajor()} follows the rule the validator
 * enforces ({@code OMNI-1047}: the minimum across payloads), and the payload {@code breaks} and
 * {@code provides} entries are filled in the way the real generator does. A fixture that violated
 * its own format would make every test built on it worthless.
 */
public final class ManifestBuilder {

    private String modId = "examplemod";
    private SemVer modVersion = SemVer.of(2, 0, 0);
    private String displayName = "Universal Example Mod";
    private String commonPackage = "com.example.common";
    private SemVer runtimeVersion = SemVer.of(1, 0, 0);
    private boolean strict = true;
    private boolean verifyIntegrity = true;
    private boolean mutualBreaks = true;
    private final EntrypointSet.Builder entrypoints = EntrypointSet.builder();
    private final List<Payload> payloads = new ArrayList<Payload>();

    /** Starts a builder with the reference container's identity. */
    public static ManifestBuilder manifest() {
        return new ManifestBuilder();
    }

    /** Sets the container's mod id. */
    public ManifestBuilder modId(String value) {
        this.modId = value;
        return this;
    }

    /** Sets the container's version. */
    public ManifestBuilder modVersion(String value) {
        this.modVersion = SemVer.parseLenient(value);
        return this;
    }

    /** Sets the display name. */
    public ManifestBuilder displayName(String value) {
        this.displayName = value;
        return this;
    }

    /** Sets the package the container's own classes live in. */
    public ManifestBuilder commonPackage(String value) {
        this.commonPackage = value;
        return this;
    }

    /** Sets the version of the nested runtime. */
    public ManifestBuilder runtimeVersion(String value) {
        this.runtimeVersion = SemVer.parseLenient(value);
        return this;
    }

    /** Sets whether a failed resolution aborts the launch. */
    public ManifestBuilder strict(boolean value) {
        this.strict = value;
        return this;
    }

    /** Sets whether payload hashes are verified at startup. */
    public ManifestBuilder verifyIntegrity(boolean value) {
        this.verifyIntegrity = value;
        return this;
    }

    /**
     * Drops the mutual {@code breaks} between payloads.
     *
     * <p>Never right for a real container — exclusivity is one of the four guarantees that make
     * "exactly one payload" hold. It exists so a conformance test can isolate the other three: with
     * breaks in place, a test of {@code provides} exclusivity would pass for the wrong reason.
     */
    public ManifestBuilder withoutMutualBreaks() {
        this.mutualBreaks = false;
        return this;
    }

    /** Declares one of the mod's entrypoint classes. */
    public ManifestBuilder entrypoint(EntrypointSet.Phase phase, String className) {
        entrypoints.add(phase, className);
        return this;
    }

    /**
     * Adds a payload covering exactly one Minecraft release.
     *
     * <p>{@code 1.21.4} becomes {@code >=1.21.4 <1.21.5}. Closed upper bounds are what the template
     * generates and what the validator prefers ({@code OMNI-1050} warns about open ones), so the
     * convenient form is also the correct one.
     *
     * @param payloadId the short id, e.g. {@code mc1214}
     * @param minecraft the Minecraft version, e.g. {@code 1.21.4}
     * @param javaMajor the Java feature version the payload needs
     * @return this builder
     */
    public ManifestBuilder payload(String payloadId, String minecraft, int javaMajor) {
        return payloadRange(payloadId, exactMinecraftRange(minecraft), javaMajor);
    }

    /**
     * Adds a payload covering a Minecraft range.
     *
     * @param payloadId the short id
     * @param minecraftRange a Fabric version predicate, e.g. {@code ">=1.21 <1.21.2"}
     * @param javaMajor the Java feature version the payload needs
     * @return this builder
     */
    public ManifestBuilder payloadRange(String payloadId, String minecraftRange, int javaMajor) {
        payloads.add(new Payload(payloadId, minecraftRange, javaMajor));
        return this;
    }

    /** Configures the payload added last. */
    public Payload lastPayload() {
        if (payloads.isEmpty()) {
            throw new IllegalStateException("add a payload first");
        }
        return payloads.get(payloads.size() - 1);
    }

    /** The minimum Java feature version across all payloads — the container's baseline. */
    public int baselineJavaMajor() {
        int baseline = Integer.MAX_VALUE;
        for (Payload payload : payloads) {
            baseline = Math.min(baseline, payload.javaMajor);
        }
        return baseline == Integer.MAX_VALUE ? 8 : baseline;
    }

    /** Builds the manifest. */
    public ContainerManifest build() {
        ContainerInfo container = ContainerInfo.builder()
                .modId(modId)
                .modVersion(modVersion)
                .displayName(displayName)
                .commonPackages(commonPackage)
                .commonPackaging(CommonPackaging.SHARED)
                .baselineJavaMajor(baselineJavaMajor())
                .runtime(new ContainerInfo.RuntimeRef(
                        OmniFormat.RUNTIME_MOD_ID,
                        runtimeVersion,
                        VersionRange.parse(">=" + runtimeVersion + " <"
                                + (runtimeVersion.major() + 1) + ".0.0"),
                        OmniFormat.NESTED_JAR_ROOT + OmniFormat.RUNTIME_MOD_ID + "-runtime-"
                                + runtimeVersion + ".jar",
                        ""))
                .minRuntime(runtimeVersion)
                .payloadAlias(modId + "-impl")
                .strict(strict)
                .verifyIntegrity(verifyIntegrity)
                .build();

        ContainerManifest.Builder manifest = ContainerManifest.builder()
                .container(container)
                .entrypoints(entrypoints.build());
        for (Payload payload : payloads) {
            manifest.payload(payload.build(this));
        }
        return manifest.build();
    }

    /** Turns {@code 1.21.4} into {@code >=1.21.4 <1.21.5} — one exact release, nothing beyond it. */
    static String exactMinecraftRange(String minecraft) {
        SemVer version = SemVer.parseLenient(minecraft);
        SemVer next = SemVer.of(version.major(), version.minor(), version.patch() + 1);
        return ">=" + version + " <" + next;
    }

    /** One payload's settings. */
    public static final class Payload {

        private final String payloadId;
        private final String minecraftRange;
        private final int javaMajor;

        private EnvironmentConstraint environment = EnvironmentConstraint.BOTH;
        private String loaderRange = ">=0.14.21";
        private String fabricApiRange;
        private int priority;
        private String platformFactory;
        private String ownPackage;
        private String[] provides;
        private final List<String> capabilities = new ArrayList<String>();

        Payload(String payloadId, String minecraftRange, int javaMajor) {
            this.payloadId = payloadId;
            this.minecraftRange = minecraftRange;
            this.javaMajor = javaMajor;
        }

        /** Restricts the payload to one physical side. */
        public Payload environment(EnvironmentConstraint value) {
            this.environment = value;
            return this;
        }

        /** Sets the minimum Fabric Loader version. */
        public Payload loader(String range) {
            this.loaderRange = range;
            return this;
        }

        /** Requires Fabric API in the given range. */
        public Payload fabricApi(String range) {
            this.fabricApiRange = range;
            return this;
        }

        /** Sets the build-time precedence used by the disjointness proof. */
        public Payload priority(int value) {
            this.priority = value;
            return this;
        }

        /** Overrides the platform factory class name. */
        public Payload platformFactory(String fqcn) {
            this.platformFactory = fqcn;
            return this;
        }

        /** Overrides the package the payload owns. */
        public Payload ownPackage(String value) {
            this.ownPackage = value;
            return this;
        }

        /** Overrides the alias ids this payload provides. */
        public Payload provides(String... values) {
            this.provides = values;
            return this;
        }

        /** Declares capabilities. */
        public Payload capabilities(String... values) {
            for (String value : values) {
                capabilities.add(value);
            }
            return this;
        }

        PayloadDescriptor build(ManifestBuilder parent) {
            String modId = parent.modId + "-" + payloadId;
            String ownedPackage = ownPackage == null
                    ? "com.example." + payloadId : ownPackage;
            String factory = platformFactory == null
                    ? ownedPackage + ".Platform" + payloadId + "Factory" : platformFactory;

            Requirements.Builder requires = Requirements.builder()
                    .minecraft(minecraftRange)
                    .fabricLoader(loaderRange)
                    .java(">=" + javaMajor)
                    .environment(environment);
            if (fabricApiRange != null) {
                requires.mod("fabric-api", fabricApiRange);
            }

            PayloadDescriptor.Builder descriptor = PayloadDescriptor.builder()
                    .id(payloadId)
                    .modId(modId)
                    .modVersion(parent.modVersion + "+" + payloadId)
                    .displayName(parent.displayName + " (" + payloadId + ")")
                    .file(OmniFormat.NESTED_JAR_ROOT + modId + ".jar")
                    .integrity("", 0L)
                    .classfileMajor(JavaVersions.classFileMajor(javaMajor))
                    .priority(priority)
                    .platformFactory(factory)
                    .packages(ownedPackage)
                    .requires(requires.build())
                    .provides(provides == null
                            ? new String[] {parent.modId + "-impl"} : provides)
                    .mappings(new MappingsInfo(MappingsInfo.INTERMEDIARY, "yarn", ""));

            for (String capability : capabilities) {
                descriptor.capabilities(capability);
            }
            // Mutual exclusion, exactly as the real generator writes it: every other payload of the
            // same container. Without it two payloads could be selected together on an overlapping
            // range, which is the failure the whole disjointness argument exists to prevent.
            if (parent.mutualBreaks) {
                for (Payload other : parent.payloads) {
                    if (other != this) {
                        descriptor.breaks(parent.modId + "-" + other.payloadId);
                    }
                }
            }
            return descriptor.build();
        }
    }
}
