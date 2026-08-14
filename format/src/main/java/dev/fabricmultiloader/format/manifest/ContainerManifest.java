package dev.fabricmultiloader.format.manifest;

import dev.fabricmultiloader.format.OmniFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The whole of {@code META-INF/omni-container.json}.
 *
 * <p>The source of truth for the runtime, the validator, the slim-jar generator and the publisher.
 * It does not, however, drive the actual payload selection — Fabric Loader does that from each
 * payload's own {@code fabric.mod.json}. This manifest is the <em>explanation</em> of the same
 * constraints, which is why the validator checks the two for equivalence ({@code OMNI-1011}) and
 * treats any divergence as a build failure rather than something to resolve at runtime.
 */
public final class ContainerManifest {

    private final String formatId;
    private final int schemaVersion;
    private final GeneratorInfo generator;
    private final ContainerInfo container;
    private final EntrypointSet entrypoints;
    private final List<PayloadDescriptor> payloads;
    private final DiagnosticsInfo diagnostics;

    private ContainerManifest(Builder builder) {
        this.formatId = builder.formatId;
        this.schemaVersion = builder.schemaVersion;
        this.generator = builder.generator;
        this.container = builder.container;
        this.entrypoints = builder.entrypoints;
        this.payloads =
                Collections.unmodifiableList(new ArrayList<PayloadDescriptor>(builder.payloads));
        this.diagnostics = builder.diagnostics;
    }

    /** Starts a builder pre-filled with the current format id and schema version. */
    public static Builder builder() {
        return new Builder();
    }

    /** Always {@code omni/1} for schema version 1. */
    public String formatId() {
        return formatId;
    }

    /** The manifest schema version. */
    public int schemaVersion() {
        return schemaVersion;
    }

    /** Which tool produced this manifest. */
    public GeneratorInfo generator() {
        return generator;
    }

    /** Identity and policy of the container. */
    public ContainerInfo container() {
        return container;
    }

    /** The mod's own entrypoint classes. */
    public EntrypointSet entrypoints() {
        return entrypoints;
    }

    /** The payloads, sorted by priority descending then id ascending. */
    public List<PayloadDescriptor> payloads() {
        return payloads;
    }

    /** Support and documentation links, printed in diagnostics. */
    public DiagnosticsInfo diagnostics() {
        return diagnostics;
    }

    /** Looks a payload up by its short id, or {@code null}. */
    public PayloadDescriptor payloadById(String id) {
        for (PayloadDescriptor payload : payloads) {
            if (payload.id().equals(id)) {
                return payload;
            }
        }
        return null;
    }

    /** Looks a payload up by its Fabric mod id, or {@code null}. */
    public PayloadDescriptor payloadByModId(String modId) {
        for (PayloadDescriptor payload : payloads) {
            if (payload.modId().equals(modId)) {
                return payload;
            }
        }
        return null;
    }

    /** Every payload's Fabric mod id, in manifest order. */
    public List<String> payloadModIds() {
        List<String> ids = new ArrayList<String>(payloads.size());
        for (PayloadDescriptor payload : payloads) {
            ids.add(payload.modId());
        }
        return Collections.unmodifiableList(ids);
    }

    /** Every capability declared by any payload, deduplicated. */
    public Set<String> allCapabilities() {
        Set<String> capabilities = new LinkedHashSet<String>();
        for (PayloadDescriptor payload : payloads) {
            capabilities.addAll(payload.capabilities());
        }
        return Collections.unmodifiableSet(capabilities);
    }

    @Override
    public String toString() {
        return container + " with " + payloads.size() + " payload(s)";
    }

    /** Provenance of a generated manifest. */
    public static final class GeneratorInfo {

        private final String tool;
        private final String version;
        private final String timestamp;
        private final String buildJdk;

        /**
         * @param tool the generating tool, normally {@code fabricmultiloader-gradle}
         * @param version the tool version
         * @param timestamp ISO-8601; fixed to the commit time or the ZIP epoch so that two builds
         *     of one commit stay byte-identical
         * @param buildJdk the JDK that produced the artifact
         */
        public GeneratorInfo(String tool, String version, String timestamp, String buildJdk) {
            this.tool = orEmpty(tool);
            this.version = orEmpty(version);
            this.timestamp = orEmpty(timestamp);
            this.buildJdk = orEmpty(buildJdk);
        }

        /** The generating tool. */
        public String tool() {
            return tool;
        }

        /** The tool version. */
        public String version() {
            return version;
        }

        /** Build timestamp; deliberately not "now". */
        public String timestamp() {
            return timestamp;
        }

        /** The JDK that produced the artifact. */
        public String buildJdk() {
            return buildJdk;
        }

        @Override
        public String toString() {
            return tool + " " + version;
        }
    }

    /** Where to send a user whose launch failed. */
    public static final class DiagnosticsInfo {

        private static final DiagnosticsInfo EMPTY = new DiagnosticsInfo("", "", "", "");

        private final String supportUrl;
        private final String documentationUrl;
        private final String downloadUrl;
        private final String contactLabel;

        /**
         * @param supportUrl issue tracker
         * @param documentationUrl the mod's documentation
         * @param downloadUrl where a newer build lives — printed when no payload matches
         * @param contactLabel human-readable name of the support channel
         */
        public DiagnosticsInfo(String supportUrl, String documentationUrl, String downloadUrl,
                String contactLabel) {
            this.supportUrl = orEmpty(supportUrl);
            this.documentationUrl = orEmpty(documentationUrl);
            this.downloadUrl = orEmpty(downloadUrl);
            this.contactLabel = orEmpty(contactLabel);
        }

        /** An instance with no links. */
        public static DiagnosticsInfo empty() {
            return EMPTY;
        }

        /** Issue tracker URL; may be empty. */
        public String supportUrl() {
            return supportUrl;
        }

        /** Documentation URL; may be empty. */
        public String documentationUrl() {
            return documentationUrl;
        }

        /** Download URL; may be empty. */
        public String downloadUrl() {
            return downloadUrl;
        }

        /** Support channel label; may be empty. */
        public String contactLabel() {
            return contactLabel;
        }

        /** Whether any link is present. */
        public boolean isEmpty() {
            return supportUrl.isEmpty() && documentationUrl.isEmpty()
                    && downloadUrl.isEmpty() && contactLabel.isEmpty();
        }
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    /** Mutable builder. */
    public static final class Builder {

        private String formatId = OmniFormat.FORMAT_ID;
        private int schemaVersion = OmniFormat.SCHEMA_VERSION;
        private GeneratorInfo generator = new GeneratorInfo("", "", "", "");
        private ContainerInfo container;
        private EntrypointSet entrypoints = EntrypointSet.empty();
        private final List<PayloadDescriptor> payloads = new ArrayList<PayloadDescriptor>();
        private DiagnosticsInfo diagnostics = DiagnosticsInfo.empty();

        /** Overrides the format id. Only a reader should ever need this. */
        public Builder formatId(String value) {
            this.formatId = value;
            return this;
        }

        /** Overrides the schema version. Only a reader should ever need this. */
        public Builder schemaVersion(int value) {
            this.schemaVersion = value;
            return this;
        }

        /** Sets the generator provenance. */
        public Builder generator(GeneratorInfo value) {
            this.generator = value;
            return this;
        }

        /** Sets the container info. */
        public Builder container(ContainerInfo value) {
            this.container = value;
            return this;
        }

        /** Sets the mod entrypoints. */
        public Builder entrypoints(EntrypointSet value) {
            this.entrypoints = value == null ? EntrypointSet.empty() : value;
            return this;
        }

        /** Adds a payload. */
        public Builder payload(PayloadDescriptor value) {
            payloads.add(value);
            return this;
        }

        /** Sets the diagnostics links. */
        public Builder diagnostics(DiagnosticsInfo value) {
            this.diagnostics = value == null ? DiagnosticsInfo.empty() : value;
            return this;
        }

        /**
         * Builds the manifest, sorting payloads into the normative order: priority descending,
         * then id ascending. Order is part of the canonical form, so a reproducible build depends
         * on it being applied here rather than left to the caller.
         */
        public ContainerManifest build() {
            if (container == null) {
                throw new IllegalStateException("manifest is missing 'container'");
            }
            Collections.sort(payloads, new java.util.Comparator<PayloadDescriptor>() {
                @Override
                public int compare(PayloadDescriptor left, PayloadDescriptor right) {
                    if (left.priority() != right.priority()) {
                        return right.priority() - left.priority();
                    }
                    return left.id().compareTo(right.id());
                }
            });
            return new ContainerManifest(this);
        }
    }
}
