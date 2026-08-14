package dev.fabricmultiloader.format.manifest;

import dev.fabricmultiloader.format.OmniFormat;
import dev.fabricmultiloader.format.version.JavaVersions;
import dev.fabricmultiloader.format.version.SemVer;
import dev.fabricmultiloader.format.version.VersionRange;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@code omni/payload.json} — a payload's description of itself, plus a copy of the container
 * identity it belongs to.
 *
 * <p>The duplication is deliberate and is what makes the development loop work. Running
 * {@code ./gradlew :versions:mc-1.21.4:runClient} launches the payload as an ordinary Fabric mod
 * with no container anywhere in the game directory, so nothing would tell the runtime which mod id
 * to report, which entrypoint classes to instantiate or which packages the platform factory is
 * allowed to live in. Carrying that here makes every payload self-sufficient (invariant I4): the
 * runtime synthesises a one-payload container from it and the lifecycle is byte-for-byte the same
 * code path as in a real universal jar.
 *
 * <p>Outside the development fallback the container manifest is authoritative and this file is read
 * only to cross-check it ({@code OMNI-2011}), because two generated files describing the same
 * payload that disagree mean the build produced something incoherent.
 */
public final class PayloadManifest {

    private final String formatId;
    private final int schemaVersion;
    private final PayloadDescriptor payload;
    private final ContainerRef container;

    private PayloadManifest(Builder builder) {
        this.formatId = builder.formatId;
        this.schemaVersion = builder.schemaVersion;
        this.payload = builder.payload;
        this.container = builder.container;
    }

    /** Starts a builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** The format identifier, {@code omni/1}. */
    public String formatId() {
        return formatId;
    }

    /** The schema version. */
    public int schemaVersion() {
        return schemaVersion;
    }

    /** This payload, as it describes itself. */
    public PayloadDescriptor payload() {
        return payload;
    }

    /** The container this payload belongs to. */
    public ContainerRef container() {
        return container;
    }

    /**
     * Builds the single-payload container manifest the development fallback runs against.
     *
     * <p>Everything the runtime needs is either recorded here or irrelevant without a container.
     * Integrity verification in particular is switched off: there is no nested jar to hash, because
     * in a development run the payload <em>is</em> the mod on disk.
     *
     * @param runtimeVersion the version of the running runtime, reported in diagnostics
     * @return a manifest describing exactly this payload
     */
    public ContainerManifest toContainerManifest(SemVer runtimeVersion) {
        ContainerInfo.Builder info = ContainerInfo.builder()
                .modId(container.modId())
                .modVersion(container.modVersion())
                .displayName(container.displayName())
                .commonPackaging(CommonPackaging.SHARED)
                .baselineJavaMajor(JavaVersions.featureVersionOf(payload.classfileMajor()))
                .runtime(new ContainerInfo.RuntimeRef(
                        OmniFormat.RUNTIME_MOD_ID, runtimeVersion, VersionRange.ALL,
                        OmniFormat.NESTED_JAR_ROOT + OmniFormat.RUNTIME_MOD_ID + "-runtime-"
                                + runtimeVersion + ".jar", ""))
                .minRuntime(runtimeVersion)
                .payloadAlias(container.modId() + "-impl")
                .strict(true)
                // No container means no nested jar, so there is nothing to hash and a check would
                // only be able to fail spuriously.
                .verifyIntegrity(false);
        for (String packageName : container.commonPackages()) {
            info.commonPackages(packageName);
        }

        return ContainerManifest.builder()
                .formatId(formatId)
                .schemaVersion(schemaVersion)
                .container(info.build())
                .entrypoints(container.entrypoints())
                .payload(payload)
                .build();
    }

    @Override
    public String toString() {
        return "payload " + payload.id() + " of " + container.modId();
    }

    /** The container identity a payload carries a copy of. */
    public static final class ContainerRef {

        private final String modId;
        private final SemVer modVersion;
        private final String displayName;
        private final List<String> commonPackages;
        private final EntrypointSet entrypoints;

        /**
         * @param modId the container's mod id
         * @param modVersion the container's version
         * @param displayName the human-readable mod name
         * @param commonPackages the package prefixes the container's own classes live in
         * @param entrypoints the mod's entrypoint classes by phase
         */
        public ContainerRef(String modId, SemVer modVersion, String displayName,
                List<String> commonPackages, EntrypointSet entrypoints) {
            this.modId = Identifiers.requireModId(modId, "container.modId");
            this.modVersion = modVersion;
            this.displayName = displayName == null ? modId : displayName;
            this.commonPackages = Collections.unmodifiableList(
                    new ArrayList<String>(commonPackages));
            this.entrypoints = entrypoints;
        }

        /** The container's mod id. */
        public String modId() {
            return modId;
        }

        /** The container's version. */
        public SemVer modVersion() {
            return modVersion;
        }

        /** The human-readable mod name. */
        public String displayName() {
            return displayName;
        }

        /** Package prefixes the container's own classes live in. */
        public List<String> commonPackages() {
            return commonPackages;
        }

        /** The mod's entrypoint classes by phase. */
        public EntrypointSet entrypoints() {
            return entrypoints;
        }

        @Override
        public String toString() {
            return modId + " " + modVersion;
        }
    }

    /** Mutable builder. */
    public static final class Builder {

        private String formatId = OmniFormat.FORMAT_ID;
        private int schemaVersion = OmniFormat.SCHEMA_VERSION;
        private PayloadDescriptor payload;
        private ContainerRef container;

        /** Sets the format identifier. */
        public Builder formatId(String value) {
            this.formatId = value;
            return this;
        }

        /** Sets the schema version. */
        public Builder schemaVersion(int value) {
            this.schemaVersion = value;
            return this;
        }

        /** Sets the payload's self-description. */
        public Builder payload(PayloadDescriptor value) {
            this.payload = value;
            return this;
        }

        /** Sets the container identity. */
        public Builder container(ContainerRef value) {
            this.container = value;
            return this;
        }

        /**
         * Builds the manifest.
         *
         * @throws IllegalStateException if the payload or the container reference is missing
         */
        public PayloadManifest build() {
            if (payload == null) {
                throw new IllegalStateException("payload manifest is missing 'payload'");
            }
            if (container == null) {
                throw new IllegalStateException("payload manifest is missing 'container'");
            }
            return new PayloadManifest(this);
        }
    }
}
